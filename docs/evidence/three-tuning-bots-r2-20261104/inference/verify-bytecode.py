"""Inspect the installed Kotlin library and compiled sampler; write bounded evidence."""
import json
import re
import subprocess
import sys
from pathlib import Path

before, after, stdlib, output = map(Path, sys.argv[1:])


def method(classpath, classname, signature):
    text = subprocess.check_output([
        "java", "-m", "jdk.jdeps/com.sun.tools.javap.Main", "-classpath", str(classpath), "-c", classname
    ], text=True)
    start = text.index(signature)
    end = re.search(r"\n  (?:public|protected|private|static)", text[start + len(signature):])
    return text[start:start + len(signature) + end.start()] if end else text[start:text.rfind("}")]


old = method(before, "at.heinz.deviceai.learning.lm.LmEval", "  public final int sample(")
new = method(after, "at.heinz.deviceai.learning.lm.LmEval", "  public final int sample(")
boxed_sum = method(stdlib, "kotlin.collections.CollectionsKt___CollectionsKt",
                   "  public static final double sumOfDouble(")
primitive_sum = method(stdlib, "kotlin.collections.ArraysKt___ArraysKt",
                       "  public static final double sum(double[])")
normalize = lambda value: re.sub(r"#\d+", "#", value)
assert normalize(old[:old.index("ireturn")]) == normalize(new[:new.index("ireturn")]), "Greedy bytecode changed"
assert "Double.valueOf" in old and "Double.valueOf" not in new, "Weight boxing was not removed"
assert "newarray       double" in new and "dastore" in new and "daload" in new
assert "sumOfDouble" in old and "ArraysKt.sum:([D)D" in new
for value in (boxed_sum, primitive_sum):
    assert "dconst_0" in value and value.count("dadd") == 1 and "dreturn" in value
Path(output).write_text(json.dumps({
    "greedyBytecodeIdenticalIgnoringConstantPoolIds": True,
    "doubleWeightBoxingRemoved": True,
    "sumStartsAtPositiveZeroAndAddsSequentially": True,
    "beforeSample": old, "afterSample": new,
    "boxedSum": boxed_sum, "primitiveSum": primitive_sum
}, indent=2) + "\n")
print("Greedy bytecode, Double boxing removal, primitive weight accesses and summation order: PASS")
