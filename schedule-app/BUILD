COMPILE_DEPS = CORE_DEPS + CLI + [
    "@onnx",
    "@comm_math//jar"
]
BUNDLES = [
    "//apps/scp:onos-apps-scp",
    "@onnx",
    "@comm_math//jar"
]

osgi_jar_with_tests(
    karaf_command_packages = ["org.onosproject.scp"],
    deps = COMPILE_DEPS,
)

onos_app(
    category = "Traffic Engineering",
    description = "Bandwidth Scheduling",
    included_bundles = BUNDLES,
    title = "Bandwidth Scheduler",
    url = "http://onosproject.org",
)

