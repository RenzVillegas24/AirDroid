# K20 kernel patch

`k20-owly-k20-kernel.patch` is the final K20 qcacld monitor-injection work on
top of EvolutionX kernel `2a67a9737583b2e6158cd8ef2dc026439bd80e4e`.

Apply from the kernel source root:

```sh
git apply /path/to/k20-owly-k20-kernel.patch
```

It includes the hidden-STA injection port, the GCC 13 `fallthrough` build fix,
and the final source state. The failed attempt to pin the hidden STA to
`<redacted-mac>` is not included; the driver-provided
`<redacted-mac>` is the working address.
