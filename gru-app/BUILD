COMPILE_DEPS = CORE_DEPS + CLI + [
    "@onnx"
]

BUNDLES = [
    "//apps/grp:onos-apps-grp",
    "@onnx"
]

osgi_jar_with_tests(
    karaf_command_packages = ["org.onosproject.grp"],
    deps = COMPILE_DEPS,
)

onos_app(
    category = "Traffic Engineering",
    description = "Traffic prediction and rouging",
    included_bundles = BUNDLES,
    title = "GRU Routing",
    url = "http://onosproject.org",
)

