package com.umamit.app

/**
 * What the setup route (SCR-04) gets from the root.
 *
 * @property onBack leaves the setup.
 * @property onOpenTimer the session waits in READY: the timer replaces the setup (TMR-01).
 * @property onOpenCatalog ⋮ *Im Katalog ansehen*: the catalog tea's detail (SCR-21) above the setup (D-098).
 * @property onOpenTea ⋮ *Tee ansehen*: the collection tea's detail (SCR-09) above the setup (D-100).
 * @property onNewVessel *Neues Gefäß …*: the vessel editor (SCR-14) above the setup (WARE-02, D-100).
 * @property newVesselId the vessel the editor saved, for the setup to select; null for none.
 * @property onConsumeNewVessel the setup selected [newVesselId]: the root forgets it.
 */
internal class SetupHost(
    val onBack: () -> Unit,
    val onOpenTimer: () -> Unit,
    val onOpenCatalog: (key: String) -> Unit = {},
    val onOpenTea: (teaId: String) -> Unit = {},
    val onNewVessel: () -> Unit = {},
    val newVesselId: String? = null,
    val onConsumeNewVessel: () -> Unit = {},
)
