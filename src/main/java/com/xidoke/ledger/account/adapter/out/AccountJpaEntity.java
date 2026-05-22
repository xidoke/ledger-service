package com.xidoke.ledger.account.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * JPA persistence model for an account — deliberately separate from the pure domain {@code Account} (ADR-0018): this
 * class carries Hibernate concerns (annotations, no-arg constructor, mutable fields) so the domain stays
 * framework-free. Mapped by {@link AccountJpaMapper}. {@code created_at}/{@code updated_at} are left to DB
 * defaults/triggers and intentionally not mapped. Plain class (no Lombok — project convention).
 */
@Entity
@Table(name = "accounts")
@SuppressWarnings("NullAway.Init") // fields are populated by Hibernate via field access, not the no-arg constructor
public class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_ref")
    private @Nullable String ownerRef;

    // currency column is CHAR(3) (ISO 4217) — tell Hibernate it is fixed-char, else schema validation
    // rejects it as VARCHAR.
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;

    protected AccountJpaEntity() {
        // required by Hibernate
    }

    public AccountJpaEntity(
            UUID id,
            @Nullable String ownerRef,
            String currency,
            String status,
            String accountType,
            long balance,
            long version) {
        this.id = id;
        this.ownerRef = ownerRef;
        this.currency = currency;
        this.status = status;
        this.accountType = accountType;
        this.balance = balance;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public @Nullable String getOwnerRef() {
        return ownerRef;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getAccountType() {
        return accountType;
    }

    public long getBalance() {
        return balance;
    }

    public long getVersion() {
        return version;
    }
}
