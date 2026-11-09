#include "zygisk/injector_identity.h"

#ifndef TCQT_INJECTOR_COMPAT_ID
#error "Build the injector through CMake to generate its implementation identity"
#endif

namespace tcqt::zygisk {
const char injector_identity[] = "TCQT_INJECTOR_ID_V1=" TCQT_INJECTOR_COMPAT_ID;
}
