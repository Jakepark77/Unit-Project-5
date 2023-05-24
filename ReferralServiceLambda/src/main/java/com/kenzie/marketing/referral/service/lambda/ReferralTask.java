package com.kenzie.marketing.referral.service.lambda;

import com.kenzie.marketing.referral.model.LeaderboardEntry;

import java.util.concurrent.Callable;

public class ReferralTask implements Callable<LeaderboardEntry> {
    @Override
    public LeaderboardEntry call() throws Exception {
        return null;
    }
}
