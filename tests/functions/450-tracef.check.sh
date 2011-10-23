#!/bin/bash

set -x

grep "int:3:3"                 450-tracef.stdout || exit 1
grep "string:4:4"              450-tracef.stdout || exit 1
grep "fraction:3.14"           450-tracef.stdout || exit 1
grep "file:file:.*/test.txt"   450-tracef.stdout || exit 1
grep "array:\[9,91,19\]"       450-tracef.stdout || exit 1
grep "pointer:.*Closed"        450-tracef.stdout || exit 1

[[ $( grep -c "WORD" 450-tracef.stdout ) == 2 ]] || exit 1
[[ $( grep "WORD" 450-tracef.stdout | wc -w ) == 5 ]] || exit 1

exit 0
