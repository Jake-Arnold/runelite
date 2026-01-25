package net.runelite.client.plugins.flippingutilities.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
public class ApiAuthHandler
{
    // Reference to the plugin
    FlippingPlugin plugin;

    @Getter
    @Setter
    private boolean hasValidJWT;

    private Set<String> successfullyRegisteredRsns = new HashSet<>();
    List<Runnable> validJwtSubscriberActions = new ArrayList<>();
    List<Consumer<Boolean>> premiumCheckSubscribers = new ArrayList<>();
    List<Consumer<Boolean>> errorsSubscribers = new ArrayList<>();

    @Getter
    @Setter
    private boolean isPremium;

    public ApiAuthHandler(FlippingPlugin plugin)
    {
        this.plugin = plugin;
        // Force a local login immediately for dev/local mode
        forceLocalLogin("LocalRSN", true);
    }

    public void subscribeToLogin(Runnable r)
    {
        this.validJwtSubscriberActions.add(r);
    }

    public void subscribeToPremiumChecking(Consumer<Boolean> consumer)
    {
        this.premiumCheckSubscribers.add(consumer);
    }

    public void subscribeToError(Consumer<Boolean> consumer)
    {
        this.errorsSubscribers.add(consumer);
    }

    public boolean canCommunicateWithApi(String displayName)
    {
        // In local mode, just check if RSN is registered
        return this.hasValidJWT && successfullyRegisteredRsns.contains(displayName);
    }

    public void setPremiumStatus()
    {
        // Always set premium locally
        forceLocalLogin("LocalRSN", true);
    }

    // Stubbed methods for local development — no API calls
    public CompletableFuture<String> checkExistingJwt()
    {
        hasValidJWT = true;
        validJwtSubscriberActions.forEach(Runnable::run);
        return CompletableFuture.completedFuture("valid jwt");
    }

    public CompletableFuture<Set<String>> checkRsn(String displayName)
    {
        successfullyRegisteredRsns.add(displayName);
        return CompletableFuture.completedFuture(successfullyRegisteredRsns);
    }

    public CompletableFuture<String> loginWithToken(String token)
    {
        hasValidJWT = true;
        validJwtSubscriberActions.forEach(Runnable::run);
        forceLocalLogin("LocalRSN", true);
        return CompletableFuture.completedFuture("local-fake-jwt");
    }

    public void forceLocalLogin(String localRsn, boolean premium)
    {
        this.hasValidJWT = true;
        this.successfullyRegisteredRsns.add(localRsn);
        this.isPremium = premium;

        // Trigger subscribers as if a real login happened
        validJwtSubscriberActions.forEach(Runnable::run);
        premiumCheckSubscribers.forEach(c -> c.accept(isPremium));
    }
}
