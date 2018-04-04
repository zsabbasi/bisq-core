/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.consensus;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.vote.PeriodService;

import bisq.common.app.Version;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if OP_RETURN data matches rules for a compensation request tx and applies state change.
 */
@Slf4j
public class OpReturnCompReqController {
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final PeriodService periodService;

    @Inject
    public OpReturnCompReqController(ReadableBsqBlockChain readableBsqBlockChain, PeriodService periodService) {
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.periodService = periodService;
    }

    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight, Model model) {
        if (model.getIssuanceCandidate() != null &&
                opReturnData.length == 22 &&
                Version.COMPENSATION_REQUEST_VERSION == opReturnData[1] &&
                bsqFee == readableBsqBlockChain.getProposalFee(blockHeight) &&
                periodService.isInPhase(blockHeight, PeriodService.Phase.PROPOSAL)) {
            txOutput.setTxOutputType(TxOutputType.COMP_REQ_OP_RETURN_OUTPUT);
            model.setVerifiedOpReturnType(OpReturnType.COMPENSATION_REQUEST);

            checkArgument(model.getIssuanceCandidate() != null,
                    "model.getCompRequestIssuanceOutputCandidate() must not be null");
            model.getIssuanceCandidate().setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
        } else {
            log.info("We expected a compensation request op_return data but it did not " +
                    "match our rules. tx={}", tx);
            txOutput.setTxOutputType(TxOutputType.INVALID_OUTPUT);

            // If the opReturn is invalid the issuance candidate cannot become BSQ, so we set it to BTC
            if (model.getIssuanceCandidate() != null)
                model.getIssuanceCandidate().setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }
}
