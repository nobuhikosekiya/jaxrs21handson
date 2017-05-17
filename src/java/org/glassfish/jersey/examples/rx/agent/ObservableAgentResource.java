/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.jersey.examples.rx.agent;

import java.util.List;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.GenericType;
import org.glassfish.jersey.client.rx.rxjava.RxObservableInvoker;
import org.glassfish.jersey.client.rx.rxjava.RxObservableInvokerProvider;
import org.glassfish.jersey.examples.rx.domain.AgentResponse;
import org.glassfish.jersey.examples.rx.domain.Calculation;
import org.glassfish.jersey.examples.rx.domain.Destination;
import org.glassfish.jersey.examples.rx.domain.Forecast;
import org.glassfish.jersey.examples.rx.domain.Recommendation;
import rx.Observable;

/**
 *
 * @author nsekiya
 */
@Singleton
@Path("agent/observable")
@Produces("application/json")
public class ObservableAgentResource {
    final private Client client = ClientBuilder.newClient();
    final private WebTarget destination = client.target("http://localhost:8080/jaxrs21/rx").path("remote/destination");
    final private WebTarget forecast = client.target("http://localhost:8080/jaxrs21/rx").path("remote/forecast/{destination}");
    final private WebTarget calculation = client.target("http://localhost:8080/jaxrs21/rx").path("remote/calculation/from/{from}/to/{to}");

    @GET
    public void observable(@Suspended final AsyncResponse async) {
        final long time = System.nanoTime();
        final AgentResponse agentResponse = new AgentResponse();

        Observable<AgentResponse> obsv = Observable.just(agentResponse)
                .zipWith(visited(), (response, visited) -> {
                    response.setVisited(visited);
                    return response;
                })
                // Obtain recommended destinations. (does not depend on visited ones)
                .zipWith(recommended(), (response, recommendations) -> {
                    response.setRecommended(recommendations);
                    return response;
                });
        
        System.out.println("before subscribe");
        
        //See how easy to duplicate the same requests
        obsv.subscribe(response -> {
                    response.setProcessingTime((System.nanoTime() - time) / 1000000);
                });
        
        obsv.subscribe(response -> {
                    response.setProcessingTime((System.nanoTime() - time) / 1000000);
                });
        
        obsv.subscribe(response -> {
                    response.setProcessingTime((System.nanoTime() - time) / 1000000);
                });
        
        obsv.subscribe(response -> {
                    response.setProcessingTime((System.nanoTime() - time) / 1000000);
                });
        
        obsv.subscribe(response -> {
                    response.setProcessingTime((System.nanoTime() - time) / 1000000);
                    async.resume(response);
                });
        
        

    }

    private Observable<List<Destination>> visited() {
        destination.register(RxObservableInvokerProvider.class);

        Observable<List<Destination>> o = destination.path("visited").request()
                          // Identify the user.
                          .header("Rx-User", "RxJava")
                          // Reactive invoker.
                          .rx(RxObservableInvoker.class)
                          // Return a list of destinations.
                          .get(new GenericType<List<Destination>>() {
                          });
        
        System.out.println("observable visited request row end");
        return o;
    }

    private Observable<List<Recommendation>> recommended() {
        destination.register(RxObservableInvokerProvider.class);

        // Recommended places.
        final Observable<Destination> recommended = destination.path("recommended").request()
                // Identify the user.
                .header("Rx-User", "RxJava")
                // Reactive invoker.
                .rx(RxObservableInvoker.class)
                // Return a list of destinations.
                .get(new GenericType<List<Destination>>() {
                })
                // Emit destinations one-by-one.
                .flatMap(Observable::from)
                // Remember emitted items for dependant requests.
                .cache();

        System.out.println("observable recommended request row end");
        
        forecast.register(RxObservableInvokerProvider.class);

        // Forecasts. (depend on recommended destinations)
        final Observable<Forecast> forecasts = recommended.flatMap(destination
                -> forecast
                        .resolveTemplate("destination", destination.getDestination())
                        .request().rx(RxObservableInvoker.class).get(Forecast.class)
        );

        System.out.println("observable forecasts request row end");
        
        calculation.register(RxObservableInvokerProvider.class);

        // Calculations. (depend on recommended destinations)
        final Observable<Calculation> calculations = recommended.flatMap(destination
                -> calculation.resolveTemplate("from", "Moon").resolveTemplate("to", destination.getDestination())
                        .request().rx(RxObservableInvoker.class).get(Calculation.class)
                        );

        System.out.println("observable calculations request row end");
        
        return Observable.zip(recommended, forecasts, calculations, Recommendation::new).toList();
    }

    
}
