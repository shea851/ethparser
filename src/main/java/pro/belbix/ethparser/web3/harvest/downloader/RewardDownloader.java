package pro.belbix.ethparser.web3.harvest.downloader;

import static java.util.Collections.singletonList;
import static pro.belbix.ethparser.utils.LoopUtils.handleLoop;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.Log;
import pro.belbix.ethparser.dto.v0.RewardDTO;
import pro.belbix.ethparser.web3.Web3Service;
import pro.belbix.ethparser.web3.contracts.ContractType;
import pro.belbix.ethparser.web3.contracts.ContractUtils;
import pro.belbix.ethparser.web3.harvest.db.RewardsDBService;
import pro.belbix.ethparser.web3.harvest.parser.RewardParser;
import pro.belbix.ethparser.web3.prices.PriceProvider;

@Service
@SuppressWarnings("rawtypes")
public class RewardDownloader {

    private static final Logger logger = LoggerFactory.getLogger(HardWorkDownloader.class);
    private final Web3Service web3Service;
    private final RewardParser rewardParser;
    private final PriceProvider priceProvider;
    private final RewardsDBService rewardsDBService;

    @Value("${reward-download.contract:}")
    private String contractName;
    @Value("${reward-download.from:}")
    private Integer from;
    @Value("${reward-download.to:}")
    private Integer to;

    public RewardDownloader(Web3Service web3Service,
                            RewardParser rewardParser,
                            PriceProvider priceProvider,
                            RewardsDBService rewardsDBService) {
        this.web3Service = web3Service;
        this.rewardParser = rewardParser;
        this.priceProvider = priceProvider;
        this.rewardsDBService = rewardsDBService;
    }

    public void start() {
        priceProvider.setUpdateBlockDifference(1);
        for (String poolName : ContractUtils.getAllPoolNames()) {
            if (contractName != null && !contractName.isBlank()
                && !contractName.equals(poolName)) {
                continue;
            }
            logger.info("Start parse rewards for " + poolName);
            handleLoop(from, to, (from, end) -> parse(from, end,
                ContractUtils.getAddressByName(poolName, ContractType.POOL)
                    .orElseThrow(() -> new IllegalStateException("Not found address by " + poolName))
            ));
        }
    }

    private void parse(Integer start, Integer end, String contract) {
        List<LogResult> logResults = web3Service.fetchContractLogs(singletonList(contract), start, end);
        if (logResults.isEmpty()) {
            logger.info("Empty log {} {}", start, end);
            return;
        }
        for (LogResult logResult : logResults) {
            try {
                RewardDTO dto = rewardParser.parseLog((Log) logResult.get());
                if (dto != null) {
                    try {
                        rewardsDBService.saveRewardDTO(dto);
                    } catch (Exception e) {
                        logger.error("error with {}", dto, e);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("error with " + logResult.get(), e);
                break;
            }
        }
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public void setTo(Integer to) {
        this.to = to;
    }
}
