package com.example.risk.feature;

import com.example.risk.model.Features;
import com.example.risk.model.TransactionEvent;

import java.util.concurrent.CompletableFuture;

public interface FeatureService {
    CompletableFuture<Features> fetch(TransactionEvent event);
}
