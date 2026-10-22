package com.hamco.site;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * BrowserRouter owns page paths after the first HTML response. Forward only
 * known page shapes: never turn a missing /api, /assets or /img URL into a 200
 * HTML response, since clients need real API and static-resource status codes.
 */
@Controller
class SpaController {
    @GetMapping({"/", "/about", "/notes", "/notes/{id}",
            "/auth/{page}", "/admin/{section}", "/admin/{section}/{action}",
            "/admin/{section}/{action}/{id}"})
    String shell() {
        return "forward:/index.html";
    }
}
