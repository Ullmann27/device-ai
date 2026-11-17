| Records | Scenario | Baseline median ms | Candidate median ms | Baseline repeat ms | Candidate / baseline | Baseline allocated MiB | Candidate allocated MiB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | cold-construction-replay | 10.297 | 20.023 | 8.233 | 1.945 | 6.381 | 7.404 |
| 256 | warm-same-type | 9.706 | 1.249 | 8.346 | 0.129 | 6.348 | 0.388 |
| 256 | caller-source48-intent96 | 9.088 | 21.568 | 9.401 | 2.373 | 12.678 | 13.438 |
| 256 | alternating-type-limit | 3.695 | 8.256 | 4.189 | 2.235 | 6.300 | 6.359 |
| 256 | mutation-and-replay | 17.471 | 16.916 | 16.970 | 0.968 | 20.161 | 20.234 |
| 1024 | cold-construction-replay | 25.084 | 26.045 | 19.723 | 1.038 | 25.635 | 25.829 |
| 1024 | warm-same-type | 16.891 | 2.332 | 14.694 | 0.138 | 25.500 | 2.214 |
| 1024 | caller-source48-intent96 | 28.352 | 28.829 | 27.909 | 1.017 | 51.007 | 51.078 |
| 1024 | alternating-type-limit | 13.014 | 13.102 | 13.218 | 1.007 | 25.483 | 25.519 |
| 1024 | mutation-and-replay | 37.210 | 41.245 | 34.942 | 1.108 | 56.028 | 56.059 |
| 4096 | cold-construction-replay | 51.009 | 55.728 | 62.892 | 1.093 | 101.909 | 101.916 |
| 4096 | warm-same-type | 49.085 | 4.925 | 57.519 | 0.100 | 101.876 | 8.621 |
| 4096 | caller-source48-intent96 | 109.221 | 116.986 | 112.356 | 1.071 | 203.753 | 203.808 |
| 4096 | alternating-type-limit | 54.123 | 49.188 | 63.187 | 0.909 | 101.873 | 101.901 |
| 4096 | mutation-and-replay | 131.050 | 134.792 | 142.123 | 1.029 | 223.992 | 224.021 |
