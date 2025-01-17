package com.k33.platform.app.invest

import com.k33.platform.email.ContentType
import com.k33.platform.email.Email
import com.k33.platform.email.MailContent
import com.k33.platform.email.getEmailService
import com.k33.platform.user.UserId
import com.k33.platform.utils.config.loadConfig
import com.k33.platform.utils.logging.getLogger
import io.firestore4k.typed.FirestoreClient
import io.firestore4k.typed.div
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object InvestService {

    private val logger by getLogger()

    private val config by loadConfig<Config>("invest", "invest")

    private val deniedCountryCodeList by lazy {
        config.deniedCountryCodeList
            .uppercase()
            .split(',')
            .map(ISO3CountyCode::valueOf)
    }

    private val emailFrom by lazy {
        config.email.from.toEmail()
    }

    private val emailToList by lazy {
        config.email.toList.toMandatoryEmailList()
    }

    private val emailCcList by lazy {
        config.email.ccList.toEmailList()
    }

    private val emailBccList by lazy {
        config.email.bccList.toEmailList()
    }

    private val emailService by getEmailService()

    private val firestoreClient by lazy { FirestoreClient() }

    fun getAllFundIds() = config.funds.keys

    fun FundInfoRequest.isApproved(fundId: FundId): Boolean {
        if (investorType == InvestorType.NON_PROFESSIONAL) {
            logger.info("Non-professional investor")
            return false
        }

        if (deniedCountryCodeList.contains(countryCode)) {
            logger.info("$countryCode is in denied list of countries")
            return false
        }

        if (config.funds[fundId.value]?.equals(fundName, ignoreCase = true) != true) {
            logger.info("Incorrect fund name")
            return false
        }

        return true
    }

    suspend fun UserId.getAllFunds(): Map<FundId, Fund?> = coroutineScope {
        config
            .funds
            .keys
            .map(::FundId)
            .associateWithAsync { fundId ->
                firestoreClient.get(inInvestAppContext() / funds / fundId)
            }
    }

    suspend fun UserId.getFund(fundId: FundId): Fund? = firestoreClient.get(inInvestAppContext() / funds / fundId)

    private suspend fun <K, V> List<K>.associateWithAsync(valueSelector: suspend (K) -> V): Map<K, V> {
        return coroutineScope {
            this@associateWithAsync.map { key ->
                async {
                    key to valueSelector(key)
                }
            }
                .awaitAll()
                .toMap()
        }
    }

    suspend fun UserId.saveStatus(
        fundId: FundId,
        status: Status,
    ) = firestoreClient.put(inInvestAppContext() / funds / fundId, Fund(status))

    suspend fun UserId.saveFundInfoRequest(
        fundId: FundId,
        fundInfoRequest: FundInfoRequest,
    ) = firestoreClient.add(inInvestAppContext() / funds / fundId / fundInfoRequests, fundInfoRequest)

    suspend fun sendEmail(
        investorEmail: String,
        fundInfoRequest: FundInfoRequest,
    ) {
        if (investorEmail.isTestUser()) {
            emailService.sendEmail(
                from = emailFrom,
                toList = listOf(Email(address = investorEmail, label = fundInfoRequest.name)),
                ccList = emailCcList,
                bccList = emailBccList,
                mail = MailContent(
                    subject = "IGNORE(testing): K33 Fund Inquiry Request",
                    contentType = ContentType.MONOSPACE_TEXT,
                    body = fundInfoRequest.asString(
                        investorEmail = investorEmail,
                    ),
                )
            )
        } else {
            emailService.sendEmail(
                from = emailFrom,
                toList = emailToList,
                ccList = emailCcList,
                bccList = emailBccList,
                mail = MailContent(
                    subject = "K33 Fund Inquiry Request",
                    contentType = ContentType.MONOSPACE_TEXT,
                    body = fundInfoRequest.asString(
                        investorEmail = investorEmail,
                    ),
                )
            )
        }
    }

    suspend fun sendSlackNotification(
        investorEmail: String,
        fundInfoRequest: FundInfoRequest,
    ) = SlackNotification.notifySlack(
        strFundInfoRequest = fundInfoRequest.asString(
            investorEmail = investorEmail,
        ),
        testMode = investorEmail.isTestUser(),
    )

    private fun String.isTestUser() = endsWith("@${config.testDomain}", ignoreCase = true)

    internal fun FundInfoRequest.asString(
        investorEmail: String,
    ): String = """
        Details of Investor submitting inquiry for the K33 Fund.
    
        Investor category ..... ${investorType.label}
        Full Name ............. $name
        Company ............... ${company ?: "-"}
        E-mail ................ $investorEmail
        Phone ................. $phoneNumber
        Country of residence .. ${countryCode?.let { "${it.displayName} (${it.name})" }}
        Name of fund .......... $fundName

        Action taken .......... Approved
        """.trimIndent()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun UserId.delete() = firestoreClient.delete(inInvestAppContext())
}

