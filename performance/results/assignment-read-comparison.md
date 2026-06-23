# Aggregate Comparison

accepted: true

## Latency and Throughput

| Metric | Before median | After median | Delta | Improvement | Interpretation |
| :--- | ---: | ---: | ---: | ---: | :--- |
| Assignment list P50 | 6.506 ms | 6.558 ms | 0.052 ms | -0.80% | regression |
| Assignment list P95 | 8.084 ms | 8.006 ms | -0.077 ms | 0.96% | inconclusive_range_overlap |
| Assignment list P99 | 10.234 ms | 9.638 ms | -0.596 ms | 5.82% | improvement |
| Assignment detail P95 | 3.570 ms | 3.901 ms | 0.331 ms | -9.29% | regression |
| Business success throughput | 99.999 req/s | 100.002 req/s | 0.002 req/s | 0.00% | fixed-rate reference |

## Query Reduction

- 60 -> 2 (96.67% reduction)
