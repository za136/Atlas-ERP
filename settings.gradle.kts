rootProject.name = "atlas-erp"

// Platform modules
include("atlas-platform:plt-common")
include("atlas-platform:plt-org")
include("atlas-platform:plt-iam")
include("atlas-platform:plt-doc")
include("atlas-platform:plt-aud")
include("atlas-platform:plt-cfg")
include("atlas-platform:plt-ai")

// Finance modules
include("atlas-finance:fin-accounting-api")
include("atlas-finance:fin-accounting-core")
include("atlas-finance:fin-gl")
include("atlas-finance:fin-ap")
include("atlas-finance:fin-ar")

// Operational domains
include("atlas-procurement")
include("atlas-sales")
include("atlas-inventory")

// Main application and integration runner
include("atlas-app")
