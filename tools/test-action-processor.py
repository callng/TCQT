"""Compile real KSP positive/negative, multi-round and incremental fixtures on the pinned toolchain."""
from pathlib import Path
import os
import re
import subprocess
import tomllib

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / 'app/build/refactor-validation/processor-fixture'
VERSIONS = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text(encoding='utf-8'))['versions']
WRAPPER = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))

def write(path, content):
    target = WORK / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')

def build(label, error=None):
    result = subprocess.run([WRAPPER, '-p', str(WORK), 'compileKotlin', '--console=plain',
                             '--configuration-cache'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding='utf-8', errors='replace')
    (WORK / f'{label}.log').write_text(result.stdout, encoding='utf-8')
    if error:
        assert result.returncode != 0 and error in result.stdout, f'{label}: expected {error}; see log'
    else:
        assert result.returncode == 0, f'{label}: compilation failed; see log'
    print(f'PASS {label}', flush=True)

def registry():
    return (WORK / 'build/generated/ksp/main/kotlin/com/owo233/tcqt/generated/GeneratedActionList.kt').read_text(encoding='utf-8')

if __name__ == '__main__':
    subprocess.run([WRAPPER, ':libs:processor:jar', ':libs:annotations:jar'], check=True)
    kotlin, ksp = VERSIONS['kotlin'], VERSIONS['ksp']
    write('settings.gradle.kts', '''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "processor-fixture"
include(":producer")
''')
    annotations = (ROOT / 'libs/annotations/build/libs/annotations.jar').as_posix()
    processor = (ROOT / 'libs/processor/build/libs/processor.jar').as_posix()
    write('build.gradle.kts', f'''plugins {{ kotlin("jvm") version "{kotlin}"; id("com.google.devtools.ksp") version "{ksp}" }}
kotlin {{ jvmToolchain(21) }}
dependencies {{ implementation(files("{annotations}")); ksp(files("{processor}", "{annotations}")); ksp(project(":producer")) }}
''')
    write('producer/build.gradle.kts', f'''plugins {{ kotlin("jvm") }}
kotlin {{ jvmToolchain(21) }}
dependencies {{ implementation("com.google.devtools.ksp:symbol-processing-api:{ksp}") }}
''')
    write('producer/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider', 'fixture.ProducerProvider\n')
    write('producer/src/main/kotlin/Producer.kt', '''package fixture
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
class ProducerProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = object : SymbolProcessor {
        var written = false
        override fun process(resolver: Resolver): List<KSAnnotated> {
            if (!written) {
                written = true
                environment.codeGenerator.createNewFile(Dependencies(false), "fixture", "GeneratedFixture").bufferedWriter().use {
                    it.write("package fixture\\nopen class GeneratedBase : com.owo233.tcqt.core.action.ActionSpec\\n@com.owo233.tcqt.annotations.RegisterAction object GeneratedAction : GeneratedBase()\\n")
                }
            }
            return emptyList()
        }
    }
}
''')
    write('src/main/kotlin/Core.kt', '''package com.owo233.tcqt.core.action
interface ActionSpec
typealias ActionProcess = com.owo233.tcqt.annotations.ActionProcess
typealias ActionPriority = com.owo233.tcqt.annotations.ActionPriority
data class ActionDescriptor(val actionClass: Class<out ActionSpec>, val processes: Set<ActionProcess>, val priority: ActionPriority)
''')
    prefix = 'package fixture\nimport com.owo233.tcqt.annotations.*\nimport com.owo233.tcqt.core.action.ActionSpec\n'
    positive = prefix + '''@RegisterAction(processes = [ActionProcess.MSF, ActionProcess.MAIN]) object Z : ActionSpec
@RegisterAction class A(val value: Int = 1) : ActionSpec
@RegisterAction object Awaited : GeneratedBase()
@RegisterAction(enabled = false) private class Disabled(val value: Int)
'''
    write('src/main/kotlin/Actions.kt', positive)
    build('positive-multi-round')
    generated = registry()
    names = re.findall(r'^        ([\w.]+)::class.java,', generated, re.M)
    assert names == ['fixture.A', 'fixture.Awaited', 'fixture.GeneratedAction', 'fixture.Z'], names
    assert 'setOf(ActionProcess.MAIN, ActionProcess.MSF)' in generated
    # Disabled declarations must remain dependencies so changing enabled invalidates the aggregate.
    write('src/main/kotlin/Actions.kt', positive.replace('object Z', 'object Y'))
    build('incremental-rename')
    assert 'fixture.Y::class.java' in registry() and 'fixture.Z::class.java' not in registry()
    write('src/main/kotlin/Actions.kt', positive.replace('@RegisterAction class A', '@RegisterAction(enabled = false) class A'))
    build('incremental-disable')
    assert 'fixture.A::class.java' not in registry()
    cases = {
        'wrong-type': ('@RegisterAction object Bad', 'must implement ActionSpec'),
        'required-constructor': ('@RegisterAction class Bad(val value: Int) : ActionSpec', 'no-argument constructor'),
        'hidden': ('@RegisterAction private object Bad : ActionSpec', 'must be public or internal'),
        'inner': ('class Outer { @RegisterAction inner class Bad : ActionSpec }', 'non-inner'),
        'generic': ('@RegisterAction class Bad<T> : ActionSpec', 'non-generic'),
        'abstract': ('@RegisterAction abstract class Bad : ActionSpec', 'must be concrete'),
        'companion': ('class Outer { @RegisterAction companion object : ActionSpec }', 'not a companion'),
        'empty-processes': ('@RegisterAction(processes = []) object Bad : ActionSpec', 'must be non-empty'),
        'duplicate-processes': ('@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MAIN]) object Bad : ActionSpec', 'no duplicates'),
        'unresolved': ('@RegisterAction object Bad : MissingBase()', 'Unresolved action registrations'),
    }
    for name, (code, message) in cases.items():
        write('src/main/kotlin/Actions.kt', prefix + code + '\n')
        build(name, message)
    write('src/main/kotlin/Actions.kt', positive)
    build('final-positive')
    assert registry() == generated, 'Generation must be deterministic across failures and input reorderings'
