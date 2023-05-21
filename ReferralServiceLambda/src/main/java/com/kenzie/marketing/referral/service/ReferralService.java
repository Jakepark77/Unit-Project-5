package com.kenzie.marketing.referral.service;

import com.kenzie.marketing.referral.model.CustomerReferrals;
import com.kenzie.marketing.referral.model.LeaderboardEntry;
import com.kenzie.marketing.referral.model.Referral;
import com.kenzie.marketing.referral.model.ReferralRequest;
import com.kenzie.marketing.referral.model.ReferralResponse;
import com.kenzie.marketing.referral.service.converter.ReferralConverter;
import com.kenzie.marketing.referral.service.dao.ReferralDao;
import com.kenzie.marketing.referral.service.exceptions.InvalidDataException;
import com.kenzie.marketing.referral.service.model.ReferralRecord;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.stream.Collectors;

public class ReferralService {

    private ReferralDao referralDao;
    private ExecutorService executor;

    @Inject
    public ReferralService(ReferralDao referralDao) {
        this.referralDao = referralDao;
        this.executor = Executors.newCachedThreadPool();
    }

    // Necessary for testing, do not delete
    public ReferralService(ReferralDao referralDao, ExecutorService executor) {
        this.referralDao = referralDao;
        this.executor = executor;
    }

    public List<LeaderboardEntry> getReferralLeaderboard() {
        Comparator<LeaderboardEntry> comparator = ((o1, o2) -> {
            if(o1.getNumReferrals() > o2.getNumReferrals()){
                return o1.getNumReferrals();
            } else if (o1.getNumReferrals() < o2.getNumReferrals()) {
                return o2.getNumReferrals();
            }else
                return Integer.parseInt(null);
        });
        TreeSet<LeaderboardEntry> leaderboard = new TreeSet<>();
        leaderboard.add(new LeaderboardEntry());
        leaderboard.add(new LeaderboardEntry());
        leaderboard.add(new LeaderboardEntry());
        leaderboard.add(new LeaderboardEntry());
        leaderboard.add(new LeaderboardEntry());
        List<ReferralRecord> referrals = referralDao.findUsersWithoutReferrerId();
        for(ReferralRecord referralRecord : referrals){
            List<Referral> referralList = getDirectReferrals(referralRecord.getCustomerId());
            int sizeOfList = referralList.size();
            LeaderboardEntry leaderboardEntry = new LeaderboardEntry(sizeOfList, referralRecord.getCustomerId());
            for(LeaderboardEntry leaderboardEntry1 : leaderboard){
                int compare = comparator.compare(leaderboardEntry1, leaderboardEntry);
                if(compare == leaderboardEntry.getNumReferrals()){
                    leaderboard.remove(leaderboard.first());
                    leaderboard.add(leaderboardEntry);
                }
            }
            }
        List<LeaderboardEntry> list = new ArrayList<>(leaderboard);
        return list;
        }



    public CustomerReferrals getCustomerReferralSummary(String customerId) {
        CustomerReferrals referrals = new CustomerReferrals();
        Integer numOfFirstReferrals = 0;
        Integer numOfSecondReferrals = 0;
        Integer numOfThirdReferrals = 0;
        for(ReferralRecord referral : referralDao.findByReferrerId(customerId)){
            numOfFirstReferrals++;
            for(ReferralRecord referralRecord : referralDao.findByReferrerId(referral.getCustomerId())){
                numOfSecondReferrals++;
                for(ReferralRecord record : referralDao.findByReferrerId(referralRecord.getCustomerId())){
                    numOfThirdReferrals++;
                }
            }
        }
        referrals.setNumFirstLevelReferrals(numOfFirstReferrals);
        referrals.setNumSecondLevelReferrals(numOfSecondReferrals);
        referrals.setNumThirdLevelReferrals(numOfThirdReferrals);
        return referrals;
    }


    public List<Referral> getDirectReferrals(String customerId) {
        List<ReferralRecord> records = referralDao.findByReferrerId(customerId);
        List<Referral> referrals = records.stream()
                .map(referralRecord -> new Referral(referralRecord.getCustomerId(),
                        referralRecord.getReferrerId(), referralRecord.getDateReferred().toString()))
                .collect(Collectors.toList());
        return referrals;
    }


    public ReferralResponse addReferral(ReferralRequest referral) {
        if (referral == null || referral.getCustomerId() == null || referral.getCustomerId().length() == 0) {
            throw new InvalidDataException("Request must contain a valid Customer ID");
        }
        ReferralRecord record = ReferralConverter.fromRequestToRecord(referral);
        referralDao.addReferral(record);
        return ReferralConverter.fromRecordToResponse(record);
    }

    public boolean deleteReferrals(List<String> customerIds){
        boolean allDeleted = true;

        if(customerIds == null){
            throw new InvalidDataException("Request must contain a valid list of Customer ID");
        }

        for(String customerId : customerIds){
            if(customerId == null || customerId.length() == 0){
                throw new InvalidDataException("Customer ID cannot be null or empty to delete");
            }

            ReferralRecord record = new ReferralRecord();
            record.setCustomerId(customerId);

            boolean deleted = referralDao.deleteReferral(record);

            if(!deleted){
                allDeleted = false;
            }
        }
        return allDeleted;
    }
}
