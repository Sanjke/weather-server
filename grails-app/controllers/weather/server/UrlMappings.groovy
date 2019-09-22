package weather.server

class UrlMappings {

    static mappings = {

        "/file/$id?" (controller: "weather", action: "file")
        "/analyze" (controller: "weather", action: "analyze")
        "/download" (controller: "weather", action: "download")
        "/"(controller: "weather", action: 'index')
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
