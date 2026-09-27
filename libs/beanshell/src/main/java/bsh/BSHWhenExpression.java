/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/
package bsh;

class BSHWhenExpression extends SimpleNode implements ParserConstants
{
    private static final long serialVersionUID = 1L;

    BSHWhenExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        int numChildren = jjtGetNumChildren();
        if ( numChildren < 2 )
            throw new EvalException("Empty when expression.", this, callstack);

        for ( int i = 1; i < numChildren; i++ ) {
            BSHWhenEntry entry = (BSHWhenEntry) jjtGetChild(i);
            if ( !entry.isElse )
                continue;
            if ( i != numChildren - 1 )
                throw new EvalException(
                    "Else branch must be the last one in when expression.",
                    this, callstack );
        }

        Node whenExp = jjtGetChild(0);
        Object whenVal = whenExp.eval(callstack, interpreter);

        for ( int i = 1; i < numChildren; i++ ) {
            BSHWhenEntry entry = (BSHWhenEntry) jjtGetChild(i);
            if ( entry.isElse )
                return entry.evalResult(callstack, interpreter);

            for ( int c = 0; c < entry.numConditions; c++ ) {
                Object targetVal = entry.jjtGetChild(c).eval(callstack, interpreter);
                if ( primitiveEquals(whenVal, targetVal, callstack, whenExp) )
                    return entry.evalResult(callstack, interpreter);
            }
        }

        throw new EvalException("No matching when branch.", this, callstack);
    }

    private boolean primitiveEquals(
        Object whenVal, Object targetVal,
        CallStack callstack, Node whenExp )
        throws EvalError
    {
        if ( whenVal == Primitive.VOID || targetVal == Primitive.VOID )
            return false;

        if ( whenVal == Primitive.NULL )
            whenVal = null;
        if ( targetVal == Primitive.NULL )
            targetVal = null;

        if ( whenVal == null || targetVal == null )
            return whenVal == targetVal;

        if ( whenVal instanceof Primitive || targetVal instanceof Primitive )
            try {
                Object result = Operators.binaryOperation(
                    whenVal, targetVal, ParserConstants.EQ );
                result = Primitive.unwrap( result );
                return result.equals( Boolean.TRUE );
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "When value: " + whenExp.getText() + ": ",
                    this, callstack );
            }
        return whenVal.equals( targetVal );
    }

    @Override
    public String toString() {
        return super.toString() + ": when";
    }
}
