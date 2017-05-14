/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.jersey.examples.rx.agent;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.glassfish.jersey.examples.rx.domain.AgentResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import org.glassfish.jersey.examples.rx.domain.Destination;
import org.glassfish.jersey.examples.rx.domain.Forecast;
import java.util.ArrayList;
import org.glassfish.jersey.examples.rx.domain.Calculation;
import org.glassfish.jersey.examples.rx.domain.Recommendation;

/**
 *
 * @author nsekiya
 */
@Path("agent/sync")
@Produces("application/json")
public class SyncAgentResource {

    final private Client client = ClientBuilder.newClient();
    final private WebTarget destination = client.target("http://localhost:8080/jaxrs21/rx").path("remote/destination");
    final private WebTarget forecast = client.target("http://localhost:8080/jaxrs21/rx").path("remote/forecast/{destination}");
    final private WebTarget calculation = client.target("http://localhost:8080/jaxrs21/rx").path("remote/calculation/from/{from}/to/{to}");

    @GET
    public AgentResponse sync() {
        final long time = System.nanoTime();

        final AgentResponse response = new AgentResponse();
        final List<Destination> visited = destination.path("visited").request()
                // Identify the user.
                .header("Rx-User", "Sync")
                // Return a list of destinations
                .get(new GenericType<List<Destination>>() {
                });

        response.setVisited(visited);
        // Obtain recommended destinations. (does not depend on visited ones)
        final List<Destination> recommended = destination.path("recommended").request()
                // Identify the user.
                .header("Rx-User", "Sync")
                // Return a list of destinations.
                .get(new GenericType<List<Destination>>() {
                });

        // Forecasts. (depend on recommended destinations)
        final List<Forecast> forecasts = new ArrayList<>(recommended.size());
        for (final Destination dest : recommended) {
            forecasts.add(forecast.resolveTemplate("destination", dest.getDestination()).request().get(Forecast.class));
        }

        // Calculations. (depend on recommended destinations)
        final List<Calculation> calculations = new ArrayList<>(recommended.size());
        for (final Destination dest : recommended) {
            calculations.add(calculation.resolveTemplate("from", "Moon").resolveTemplate("to", dest.getDestination())
                    .request().get(Calculation.class));
        }

        // Recommendations.
        final List<Recommendation> recommendations = new ArrayList<>(recommended.size());
        for (int i = 0; i < recommended.size(); i++) {
            recommendations.add(new Recommendation(recommended.get(i).getDestination(), forecasts.get(i).getForecast(),
                    calculations.get(i).getPrice()));
        }

        response.setRecommended(recommendations);

        response.setProcessingTime((System.nanoTime() - time) / 1000000);
        return response;
    }
}
