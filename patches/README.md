# K20 kernel source

The old monolithic `k20-owly-k20-kernel.patch` was removed. The complete K20
kernel state is now carried by the pinned `kernel/` submodule:

```text
RenzVillegas24/kernel_xiaomi_raphael
branch: owly-k20
commit: e7f71fa7c64f76268407922762c061805fdfeae1
```

It includes the hidden-STA injection port, the GCC 13 `fallthrough` build fix,
the camera/IPA Makefile include fixes, and the final source state.

```bash
git submodule update --init kernel
```

The driver-provided hidden-STA address is used at runtime; no device-specific
address is committed.
