# Network and cryptography notes, for a reviewer

## Plain http to the reader's own server

Audio Reader can read the library of an Audiobookshelf server the reader runs, usually on the home network without a certificate. Android refuses plain http by default, so `res/xml/network_security_config.xml` permits it; permitting
cleartext does not weaken https, it only stops Android refusing http. Android cannot allow it for a range of
addresses only, so it is allowed everywhere. An address typed without a scheme is https; plain http is used only when typed, with a warning for a host that is not on a home network (`ServerScreen.kt`). A scan that flags `cleartextTrafficPermitted` is seeing this
and not an accident: removing it would stop every home server that has no certificate from working.
