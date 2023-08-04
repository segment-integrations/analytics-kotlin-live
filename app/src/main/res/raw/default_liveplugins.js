console.log("starting example...");

class TestSuper extends LivePlugin {
    constructor(type, destination) {
        console.log("js: TestSuper.constructor() called")
        super(type, destination);
    }

    update(settings, initialUpdate) {
        console.log("js: TestSuper.update() called")
        if (initialUpdate == true) {
            console.log(settings)
        }
    }

    execute(event) {
        console.log("js: TestSuper.execute() called");
        return super.execute(event);
    }

    track(event) {
        console.log("js: TestSuper.track() called")
        event.context.livePluginMessage = "This came from a LivePlugin";
        const mcvid = DataBridge["mcvid"]
        if (mcvid) {
            event.context.mcvid = mcvid;
        }
        return event
    }
};

class AnonymizeIPs extends LivePlugin {
    execute(event) {
        event.context.ip = "xxx.xxx.xxx.xxx";
        return super.execute(event)
    }
}

// EdgeFn example end -------------------------------------------

const userRegisteredEventProps = {
    plan: "Pro Annual",
    accountType : "Facebook"
}

const checkoutEventProps = {
    amount: "$1337.00"
}

let a = new Analytics("HO63Z36e0Ufa8AAgbjDomDuKxFuUICqI", analytics);
_ = a.track("testtest")
_ = a.track("userRegisteredEvent", userRegisteredEventProps);
_ = a.track("checkoutEvent", checkoutEventProps);
a.identify("newUser", {behaviour:"Bad"});
a.flush();

let fn = new TestSuper(LivePluginType.enrichment, null);
analytics.add(fn);