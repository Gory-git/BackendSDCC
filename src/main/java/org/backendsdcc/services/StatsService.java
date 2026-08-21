package org.backendsdcc.services;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.ReceiptRepository;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.PaymentMethodStatDTO;
import org.backendsdcc.support.dto.ProductStatDTO;
import org.backendsdcc.support.dto.RevenuePointDTO;
import org.backendsdcc.support.dto.SummaryStatsDTO;
import org.backendsdcc.support.dto.UserStatDTO;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class StatsService
{
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private UserRepository userRepository;

    private void validateRange(Instant dateMin, Instant dateMax) throws InvalidRequestException
    {
        if (!DateValidator.isValid(dateMin))
            throw new InvalidRequestException("Invalid min date given");
        if (!DateValidator.isValid(dateMax))
            throw new InvalidRequestException("Invalid max date given");
        if (dateMin.isAfter(dateMax))
            throw new InvalidRequestException("Invalid date range given");
    }

    @Transactional(readOnly = true)
    public List<RevenuePointDTO> getRevenueOverTime(Instant dateMin, Instant dateMax) throws InvalidRequestException
    {
        validateRange(dateMin, dateMax);
        List<Receipt> receipts = receiptRepository.findByDateBetween(dateMin, dateMax);

        Map<LocalDate, RevenuePointDTO> byDay = new TreeMap<>();
        for (Receipt receipt : receipts)
        {
            LocalDate day = receipt.getDate().atZone(ZoneOffset.UTC).toLocalDate();
            RevenuePointDTO point = byDay.computeIfAbsent(day, d -> new RevenuePointDTO(d.toString(), BigDecimal.ZERO, 0L));
            point.setTotal(point.getTotal().add(receipt.getAmount()));
            point.setCount(point.getCount() + 1);
        }
        return new ArrayList<>(byDay.values());
    }

    @Transactional(readOnly = true)
    public List<ProductStatDTO> getTopProducts(Instant dateMin, Instant dateMax, int limit) throws InvalidRequestException
    {
        validateRange(dateMin, dateMax);
        if (limit <= 0)
            throw new InvalidRequestException("Invalid limit given");

        List<Receipt> receipts = receiptRepository.findByDateBetweenWithPurchases(dateMin, dateMax);

        Map<Product, ProductStatDTO> statsByProduct = new HashMap<>();
        for (Receipt receipt : receipts)
        {
            for (Purchase purchase : receipt.getPurchases())
            {
                Product product = purchase.getProduct();
                ProductStatDTO stat = statsByProduct.computeIfAbsent(product, p ->
                {
                    ProductStatDTO dto = new ProductStatDTO();
                    dto.setProductCode(p.getCode());
                    dto.setProductName(p.getName());
                    dto.setQuantity(0L);
                    dto.setRevenue(BigDecimal.ZERO);
                    return dto;
                });
                stat.setQuantity(stat.getQuantity() + purchase.getQuantity());
                stat.setRevenue(stat.getRevenue().add(purchase.getPrice().multiply(BigDecimal.valueOf(purchase.getQuantity()))));
            }
        }

        return statsByProduct.values().stream()
                .sorted(Comparator.comparingLong(ProductStatDTO::getQuantity).reversed())
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodStatDTO> getPaymentMethodBreakdown(Instant dateMin, Instant dateMax) throws InvalidRequestException
    {
        validateRange(dateMin, dateMax);
        List<Receipt> receipts = receiptRepository.findByDateBetween(dateMin, dateMax);

        Map<PaymentMethod, PaymentMethodStatDTO> statsByMethod = new EnumMap<>(PaymentMethod.class);
        for (Receipt receipt : receipts)
        {
            PaymentMethodStatDTO stat = statsByMethod.computeIfAbsent(receipt.getPaymentMethod(), method ->
            {
                PaymentMethodStatDTO dto = new PaymentMethodStatDTO();
                dto.setPaymentMethod(method);
                dto.setCount(0L);
                dto.setTotal(BigDecimal.ZERO);
                return dto;
            });
            stat.setCount(stat.getCount() + 1);
            stat.setTotal(stat.getTotal().add(receipt.getAmount()));
        }

        return new ArrayList<>(statsByMethod.values());
    }

    @Transactional(readOnly = true)
    public List<UserStatDTO> getTopUsers(Instant dateMin, Instant dateMax, int limit) throws InvalidRequestException
    {
        validateRange(dateMin, dateMax);
        if (limit <= 0)
            throw new InvalidRequestException("Invalid limit given");

        List<Receipt> receipts = receiptRepository.findByDateBetween(dateMin, dateMax);

        Map<User, UserStatDTO> statsByUser = new HashMap<>();
        for (Receipt receipt : receipts)
        {
            User user = receipt.getUser();
            UserStatDTO stat = statsByUser.computeIfAbsent(user, u ->
            {
                UserStatDTO dto = new UserStatDTO();
                dto.setEmail(u.getEmail());
                dto.setName(u.getName());
                dto.setSurname(u.getSurname());
                dto.setTotalSpent(BigDecimal.ZERO);
                dto.setReceiptCount(0L);
                return dto;
            });
            stat.setTotalSpent(stat.getTotalSpent().add(receipt.getAmount()));
            stat.setReceiptCount(stat.getReceiptCount() + 1);
        }

        return statsByUser.values().stream()
                .sorted(Comparator.comparing(UserStatDTO::getTotalSpent).reversed())
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public SummaryStatsDTO getSummary(Instant dateMin, Instant dateMax) throws InvalidRequestException
    {
        validateRange(dateMin, dateMax);
        List<Receipt> receipts = receiptRepository.findByDateBetween(dateMin, dateMax);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Receipt receipt : receipts)
            totalRevenue = totalRevenue.add(receipt.getAmount());

        long receiptCount = receipts.size();
        BigDecimal averageReceipt = receiptCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(receiptCount), 2, java.math.RoundingMode.HALF_UP);

        long userCount = userRepository.count();
        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole().equals("ROLE_ADMIN"))
                .count();

        SummaryStatsDTO summary = new SummaryStatsDTO();
        summary.setTotalRevenue(totalRevenue);
        summary.setReceiptCount(receiptCount);
        summary.setAverageReceipt(averageReceipt);
        summary.setUserCount(userCount);
        summary.setAdminCount(adminCount);
        return summary;
    }
}
