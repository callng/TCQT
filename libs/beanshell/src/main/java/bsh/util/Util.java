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
package bsh.util;

import java.lang.reflect.Array;

/**
    Misc utilities for the bsh.util package.
    Nothing in the core language (bsh package) should depend on this.
    Note: that promise is currently broken... fix it.
*/
public class Util
{
    /** Just concat 2 arrays */
    @SuppressWarnings("unchecked")
    public static <T> T[] concatArrays(T[] ...arrays) {
        if (arrays.length == 0) throw new NullPointerException("There is no arrays to concat!");
        if (arrays.length == 1) return arrays[0];

        int totalLength = 0;
        for (T[] array: arrays) totalLength += array.length;

        final Class<?> baseType = arrays[0].getClass().getComponentType();
        T[] result = (T[]) Array.newInstance(baseType, totalLength);

        int offset = 0;
        for (T[] array: arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        // final Class<?> baseType = a.getClass().getComponentType();
        // T[] result = (T[]) Array.newInstance(baseType, a.length + b.length);

        // System.arraycopy(baseType, 0, result, 0, 0);
        // for (int i = 0; i < a.length; i++) result[i] = a[i];
        // for (int i = 0; i < b.length; i++) result[i + a.length] = b[i];

        return result;
    }
}
