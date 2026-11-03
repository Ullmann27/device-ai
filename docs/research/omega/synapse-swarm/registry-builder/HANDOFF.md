# Synapse identity builder — WIP

Baseline efe622dda00e620952483dedad19257efb3284e6. EXP-11/EXP-12.
Independent acceptance frozen by separate reviewer at c5075e649ef00f6addef338a31e25fd22edaf505 before implementation.

Historical PR384 has the actual in-memory specialist registry inside ChiefDelegationCoordinator
(source34543f9c6cb94c8dc471099dd656ad932d690b96), not a separate persistent registry.
This lane selectively brings that coordinator forward and extends it with durable identity
metadata; execution admission remains its default-deny trusted owner callback and existing pool.
Profile descriptions are not verified capabilities. No existing owner store or owner facts are changed.

Private working notes use the existing FileContinuumObjectStore in separate namespaces.
Registry APIs are trusted app seams, not an OS sandbox against same-UID code.
A bound directory does not establish an Android container or a running model.

Implementation/tests NOT_RUN; model/Fold7 unavailable. No main merge, release, signing or install.
