/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.wire.payload.payment;

import io.bisq.common.app.Version;
import io.bisq.wire.proto.Messages;

public final class OKPayAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String accountNr;

    public OKPayAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public OKPayAccountContractData(String paymentMethodName, String id, long maxTradePeriod, String accountNr) {
        super(paymentMethodName, id, maxTradePeriod);
        this.accountNr = accountNr;
    }

    public void setAccountNr(String accountNr) {
        this.accountNr = accountNr;
    }

    public String getAccountNr() {
        return accountNr;
    }

    @Override
    public String getPaymentDetails() {
        return "OKPay - Account no.: " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.OKPayAccountContractData.Builder builder = Messages.OKPayAccountContractData.newBuilder();
        Messages.OKPayAccountContractData.Builder thisClass =
                builder.setAccountNr(accountNr);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setOKPayAccountContractData(thisClass);
        return paymentAccountContractData.build();
    }
}