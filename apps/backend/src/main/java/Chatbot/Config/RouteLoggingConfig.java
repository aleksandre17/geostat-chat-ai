package Chatbot.Config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Configuration("chatRouteLoggingConfig")
public class RouteLoggingConfig {

    private final RequestMappingHandlerMapping handlerMapping;

    public RouteLoggingConfig(@Qualifier("controllerEndpointHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRoutes() {
        Map<RequestMappingInfo, HandlerMethod> map = handlerMapping.getHandlerMethods();

        List<RouteEntry> routes = new ArrayList<>();
        map.forEach((mapping, method) -> {
            Set<String> patterns = mapping.getPatternValues();
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();

            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    routes.add(new RouteEntry("ALL", pattern,
                            method.getBeanType().getSimpleName(), method.getMethod().getName()));
                } else {
                    for (RequestMethod m : methods) {
                        routes.add(new RouteEntry(m.name(), pattern,
                                method.getBeanType().getSimpleName(), method.getMethod().getName()));
                    }
                }
            }
        });

        routes.sort(Comparator.comparing(RouteEntry::pattern).thenComparing(RouteEntry::httpMethod));

        // Calculate column widths
        int methodWidth = routes.stream().mapToInt(r -> r.httpMethod.length()).max().orElse(6);
        int patternWidth = routes.stream().mapToInt(r -> r.pattern.length()).max().orElse(30);
        int controllerWidth = routes.stream().mapToInt(r -> r.controller.length()).max().orElse(20);

        methodWidth = Math.max(methodWidth, 6);
        patternWidth = Math.max(patternWidth, 10);
        controllerWidth = Math.max(controllerWidth, 10);

        String fmt = "  %-" + methodWidth + "s │ %-" + patternWidth + "s │ %s#%s()";

        // Group by controller
        Map<String, List<RouteEntry>> grouped = routes.stream()
                .collect(Collectors.groupingBy(RouteEntry::controller, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  Mobile API — %d endpoints registered on port ${server.port}%s║%n",
                routes.size(), " ".repeat(Math.max(1, 35 - String.valueOf(routes.size()).length()))));
        sb.append("╠══════════════════════════════════════════════════════════════════════════╣\n");

        for (Map.Entry<String, List<RouteEntry>> entry : grouped.entrySet()) {
            sb.append(String.format("║  ── %s%s║%n", entry.getKey(),
                    " ".repeat(Math.max(1, 69 - entry.getKey().length()))));
            for (RouteEntry r : entry.getValue()) {
                String line = String.format(fmt, r.httpMethod, r.pattern, r.controller, r.method);
                sb.append(String.format("║%-73s║%n", line));
            }
        }

        sb.append("╚══════════════════════════════════════════════════════════════════════════╝");

        log.info(sb.toString());
    }

    private record RouteEntry(String httpMethod, String pattern, String controller, String method) {}
}

