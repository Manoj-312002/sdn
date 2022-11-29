COMPILE_DEPS = CORE_DEPS + JACKSON + [
    "@concurrent_trees//jar",
]

osgi_jar(
    deps = COMPILE_DEPS,
)

onos_app(
    category = "Traffic Engineering",
    description = "Traffic prediction and rouging",
    title = "LSTM Routing",
    url = "http://onosproject.org",
)
