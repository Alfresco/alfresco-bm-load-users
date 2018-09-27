package org.alfresco.bm.user;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

@Component
public class MeteringUtil
{
    @Autowired
    ApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;

    // successful REST calls to the target Alfresco server
    // this usually means status codes like 200 or 201
    private volatile Counter restCallsSuccessful;

    // failed REST calls to the target Alfresco server
    // this counts response status codes like 4XX and 5XX
    private volatile Counter restCallsFailed;

    // tolerable REST calls to the target Alfresco server
    // this includes response status codes that indicate the resource already exists
    private volatile Counter restCallsTolerable;

    // time it took to make a REST call request
    private volatile Timer restCallTime;

    // time it took to process an event
    private volatile LongTaskTimer eventProcessTime;

    // measure number or requests made each second by the bm framework
    // each second may be too small of an interval. we may consider minute
    private volatile Gauge numberOfRequestsPerTime;

    private volatile AtomicInteger numberOfRequestsPerSecond = new AtomicInteger();

    // we wil also monitor CPU load, MEM consumption, and other VM metrics offered out of the box

    @EventListener(ApplicationReadyEvent.class)
    public void init()
    {
        System.out.println("We are initializing MeteringUtil now!!!!!!");

        restCallsSuccessful = meterRegistry.counter("rest.calls.successful");
        restCallsFailed = meterRegistry.counter("rest.calls.failed");
        restCallsTolerable = meterRegistry.counter("rest.calls.tolerable");

        restCallTime = meterRegistry.timer("rest.call.time");
        eventProcessTime = LongTaskTimer.builder("event.process.time").register(meterRegistry);

        final ToDoubleFunction<AtomicInteger> toDoubleFunction = new ToDoubleFunction<AtomicInteger>()
        {
            @Override
            public double applyAsDouble(AtomicInteger value)
            {
                return value.doubleValue();
            }
        };
        numberOfRequestsPerTime = Gauge.builder("number.requests.per.time", numberOfRequestsPerSecond, toDoubleFunction).register(meterRegistry);
    }

    public Counter getRestCallsSuccessful()
    {
        return restCallsSuccessful;
    }

    public Counter getRestCallsFailed()
    {
        return restCallsFailed;
    }

    public Counter getRestCallsTolerable()
    {
        return restCallsTolerable;
    }

    public Timer getRestCallTime()
    {
        return restCallTime;
    }

    public LongTaskTimer getEventProcessTime()
    {
        return eventProcessTime;
    }

    private Set<Long> events = new HashSet();

    public synchronized void registerCall()
    {
        final long tNow = System.currentTimeMillis();
        events.add(tNow);
        //quick clean
        final Long tASecondAgo = tNow - 1000;
        for (Long t : events.toArray(new Long[events.size()]))
        {
            if (t.compareTo(tASecondAgo) < 0)
            {
                events.remove(t);
            }
        }
        numberOfRequestsPerSecond.set(events.size());

    }

}
