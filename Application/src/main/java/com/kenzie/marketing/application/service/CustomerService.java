package com.kenzie.marketing.application.service;

import com.kenzie.marketing.application.controller.model.CreateCustomerRequest;
import com.kenzie.marketing.application.controller.model.CustomerResponse;
import com.kenzie.marketing.application.controller.model.LeaderboardUiEntry;
import com.kenzie.marketing.application.repositories.CustomerRepository;
import com.kenzie.marketing.application.repositories.model.CustomerRecord;
import com.kenzie.marketing.referral.model.CustomerReferrals;
import com.kenzie.marketing.referral.model.LeaderboardEntry;
import com.kenzie.marketing.referral.model.Referral;
import com.kenzie.marketing.referral.model.ReferralRequest;
import com.kenzie.marketing.referral.model.client.ReferralServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.UUID.randomUUID;

@Service
public class CustomerService {
    private static final Double REFERRAL_BONUS_FIRST_LEVEL = 10.0;
    private static final Double REFERRAL_BONUS_SECOND_LEVEL = 3.0;
    private static final Double REFERRAL_BONUS_THIRD_LEVEL = 1.0;

    private CustomerRepository customerRepository;
    private ReferralServiceClient referralServiceClient;

    public CustomerService(CustomerRepository customerRepository, ReferralServiceClient referralServiceClient) {
        this.customerRepository = customerRepository;
        this.referralServiceClient = referralServiceClient;
    }

    /**
     * findAllCustomers
     * @return A list of Customers
     */
    public List<CustomerResponse> findAllCustomers() {
        List<CustomerRecord> records = StreamSupport.stream(customerRepository.findAll().spliterator(), true).collect(Collectors.toList());
        return records.stream()
                .map(this::createCustomerResponse)
                .collect(Collectors.toList());
    }

    /**
     * findByCustomerId
     * @param customerId
     * @return The Customer with the given customerId
     */
    public CustomerResponse getCustomer(String customerId) {
        Optional<CustomerRecord> record = customerRepository.findById(customerId);
        if(record.isPresent()) {
            return createCustomerResponse(record.get());
        }
        return null;
    }

    /**
     * addNewCustomer
     * This creates a new customer.  If the referrerId is included, the referrerId must be valid and have a
     * corresponding customer in the DB.  This posts the referrals to the referral service
     * @param createCustomerRequest
     * @return A CustomerResponse describing the customer
     */
    public CustomerResponse addNewCustomer(CreateCustomerRequest createCustomerRequest) {
        if(createCustomerRequest.getReferrerId().isPresent()) {
            if (customerRepository.existsById(createCustomerRequest.getReferrerId().toString())) {
                CustomerRecord record = new CustomerRecord();
                record.setName(createCustomerRequest.getName());
                record.setId(randomUUID().toString());
                record.setReferrerId(createCustomerRequest.getReferrerId().toString());
                record.setDateCreated(LocalDateTime.now().toString());
                customerRepository.save(record);
                ReferralRequest referralRequest = new ReferralRequest();
                referralRequest.setCustomerId(record.getId());
                referralRequest.setReferrerId(createCustomerRequest.getReferrerId().toString());
                referralServiceClient.addReferral(referralRequest);
                return createCustomerResponse(record);
            }
            return null;
        }
        CustomerRecord record = new CustomerRecord();
        record.setName(createCustomerRequest.getName());
        record.setId(randomUUID().toString());
        record.setReferrerId(null);
        record.setDateCreated(LocalDateTime.now().toString());
        customerRepository.save(record);
        ReferralRequest referralRequest = new ReferralRequest();
        referralRequest.setCustomerId(record.getId());
        referralRequest.setReferrerId(null);
        referralServiceClient.addReferral(referralRequest);
        return createCustomerResponse(record);
    }


    /**
     * updateCustomer - This updates the customer name for the given customer id
     * @param customerId - The id of the customer to update
     * @param customerName - The new name for the customer
     */
    public CustomerResponse updateCustomer(String customerId, String customerName) {
        Optional<CustomerRecord> customerExists = customerRepository.findById(customerId);
        if (customerExists.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer Not Found");
        }
        CustomerRecord customerRecord = customerExists.get();
        customerRecord.setName(customerName);
        customerRepository.save(customerRecord);
        return createCustomerResponse(customerRecord);
    }

    /**
     * deleteCustomer - This deletes the customer record for the given customer id
     * @param customerId
     */
    public void deleteCustomer(String customerId) {
        customerRepository.deleteById(customerId);
    }

    /**
     * calculateBonus - This calculates the referral bonus for the given customer according to the referral bonus
     * constants.
     * @param customerId
     * @return
     */
    public Double calculateBonus(String customerId) {
        CustomerReferrals referrals = referralServiceClient.getReferralSummary(customerId);

        Double calculationResult = REFERRAL_BONUS_FIRST_LEVEL * referrals.getNumFirstLevelReferrals() +
                REFERRAL_BONUS_SECOND_LEVEL * referrals.getNumSecondLevelReferrals() +
                REFERRAL_BONUS_THIRD_LEVEL * referrals.getNumThirdLevelReferrals();

        return calculationResult;
    }

    /**
     * getReferrals - This returns a list of referral entries for every customer directly referred by the given
     * customerId.
     * @param customerId
     * @return
     */
    public List<CustomerResponse> getReferrals(String customerId) {
        if(customerRepository.existsById(customerId)) {
            List<Referral> referrals = referralServiceClient.getDirectReferrals(customerId);
            return referrals.stream()
                    .map(referral -> getCustomer(referral.getCustomerId()))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException();
    }

    /**
     * getLeaderboard - This calls the referral service to retrieve the current top 5 leaderboard of the most referrals
     * @return
     */
    public List<LeaderboardUiEntry> getLeaderboard() {
        List<LeaderboardEntry> listOfLeaders = referralServiceClient.getLeaderboard();

        return listOfLeaders.stream()
                .map(leaderboardEntry -> {
                    CustomerResponse response = getCustomer(leaderboardEntry.getCustomerId());
                    if(response.getName() == null){
                        response.setName("no name found");
                    }
                    return new LeaderboardUiEntry(leaderboardEntry.getCustomerId(),
                            response.getName(), leaderboardEntry.getNumReferrals());
                })
                .collect(Collectors.toList());
    }

    private CustomerResponse createCustomerResponse(CustomerRecord customerRecord){
        if(customerRecord.getReferrerId() == null){
            CustomerResponse response = new CustomerResponse();
            response.setId(customerRecord.getId());
            response.setName(customerRecord.getName());
            response.setReferrerId(null);
            response.setDateJoined(customerRecord.getDateCreated());
            response.setReferrerName(null);
            return response;
        }
        CustomerResponse response = new CustomerResponse();
        response.setId(customerRecord.getId());
        response.setName(customerRecord.getName());
        response.setReferrerId(customerRecord.getReferrerId());
        response.setDateJoined(customerRecord.getDateCreated());
        Optional<CustomerRecord> record = customerRepository.findById(customerRecord.getReferrerId());
        response.setReferrerName(record.get().getName());
        return response;
    }
    /* -----------------------------------------------------------------------------------------------------------
        Private Methods
       ----------------------------------------------------------------------------------------------------------- */

    // Add any private methods here

}
