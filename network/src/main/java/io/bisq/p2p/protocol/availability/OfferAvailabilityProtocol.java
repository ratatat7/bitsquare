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

package io.bisq.p2p.protocol.availability;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.messages.DecryptedDirectMessageListener;
import io.bisq.messages.Message;
import io.bisq.messages.availability.OfferAvailabilityResponse;
import io.bisq.messages.availability.OfferMessage;
import io.bisq.messages.trade.offer.payload.OfferPayload;
import io.bisq.messages.util.Validator;
import io.bisq.protocol.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bisq.protocol.availability.tasks.SendOfferAvailabilityRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(OfferAvailabilityProtocol.class);

    private static final long TIMEOUT_SEC = 60;

    private final OfferAvailabilityModel model;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;

    private TaskRunner<OfferAvailabilityModel> taskRunner;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferAvailabilityProtocol(OfferAvailabilityModel model, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peersNodeAddress) -> {
            Message message = decryptedMessageWithPubKey.message;
            if (message instanceof OfferMessage) {
                OfferMessage offerMessage = (OfferMessage) message;
                Validator.nonEmptyStringOf(offerMessage.offerId);
                if (message instanceof OfferAvailabilityResponse
                        && model.offer.getId().equals(offerMessage.offerId)) {
                    log.trace("handle OfferAvailabilityResponse = " + message.getClass().getSimpleName() + " from " + peersNodeAddress);
                    handle((OfferAvailabilityResponse) message);
                }
            }
        };
    }

    private void cleanup() {
        stopTimeout();
        model.p2PService.removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendOfferAvailabilityRequest() {
        // reset
        model.offer.setState(OfferPayload.State.UNDEFINED);

        model.p2PService.addDecryptedDirectMessageListener(decryptedDirectMessageListener);
        model.setPeerNodeAddress(model.offer.getOffererNodeAddress());

        taskRunner = new TaskRunner<>(model,
                () -> log.debug("sequence at sendOfferAvailabilityRequest completed"),
                (errorMessage) -> {
                    log.error(errorMessage);
                    stopTimeout();
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(SendOfferAvailabilityRequest.class);
        startTimeout();
        taskRunner.run();
    }

    public void cancel() {
        taskRunner.cancel();
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(OfferAvailabilityResponse message) {
        stopTimeout();
        startTimeout();
        model.setMessage(message);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handle OfferAvailabilityResponse completed");
                    stopTimeout();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                    stopTimeout();
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessOfferAvailabilityResponse.class);
        taskRunner.run();
    }

    private void startTimeout() {
        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.debug("Timeout reached at " + this);
                model.offer.setState(OfferPayload.State.OFFERER_OFFLINE);
                errorMessageHandler.handleErrorMessage("Timeout reached: Peer has not responded.");
            }, TIMEOUT_SEC);
        } else {
            log.warn("timeoutTimer already created. That must not happen.");
        }
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
