package com.narturebelle.service;

import com.narturebelle.model.Booking;
import com.narturebelle.model.Payout;
import com.narturebelle.model.PayoutRate;
import com.narturebelle.model.PayoutStatus;
import com.narturebelle.repository.PayoutRateRepository;
import com.narturebelle.repository.PayoutRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** amount is copied from PayoutRate at completion time and never re-read later — same rule as Order.price. */
@Component
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutRateRepository payoutRateRepository;

    public PayoutService(PayoutRepository payoutRepository, PayoutRateRepository payoutRateRepository) {
        this.payoutRepository = payoutRepository;
        this.payoutRateRepository = payoutRateRepository;
    }

    public Payout createPayoutForBooking(Booking booking) {
        BigDecimal amount = payoutRateRepository.findByProfessionalType(booking.getProfessionalType())
                .map(PayoutRate::getAmount)
                .orElse(BigDecimal.ZERO);

        Payout payout = new Payout();
        payout.setBookingId(booking.getId());
        payout.setProviderId(booking.getProviderId());
        payout.setAmount(amount);
        payout.setStatus(PayoutStatus.PENDING);
        return payoutRepository.save(payout);
    }

    public List<Payout> listForProvider(Long providerId) {
        return payoutRepository.findByProviderId(providerId);
    }
}
