package f1

import java.math.RoundingMode

class F1_1 {
    static def DT_CURRENCY_CODE
    static def R_VALUE_CALC
    static def ERR_MESSAGE
    static Object[][] SUPP_COSTS

    static String getResult(Map<String, Object> param, String chargeName) {
        ERR_MESSAGE = null
        R_VALUE_CALC = calculate(param, chargeName)
        if (!R_VALUE_CALC) {
            return ERR_MESSAGE
        } else {
            return chargeName + " | " + R_VALUE_CALC + " " + DT_CURRENCY_CODE
        }
    }

    static BigDecimal calculate(Map<String, Object> param, String chargeName) {

        ArrayList freightCharges = ["CHARGE_NAME_1", "CHARGE_NAME_2", "CHARGE_NAME_3"]
        ArrayList handlingCharges = ["CHARGE_NAME_5", "CHARGE_NAME_6"]

        DT_CURRENCY_CODE = "USD"
        R_VALUE_CALC = BigDecimal.ZERO

        /** STEP 1. Validate. **/

        if (!(param.get("FT00443_FR_CHRG")) && !(param.get("FT00443_HNDL_CHRG"))) {
            ERR_MESSAGE = "Missing both freight and handling surcharges trigger."
            return
        }

        int unitType
        if (param.get("FT00443_FR_CHRG")) {
            unitType = 1
            if (!freightCharges.contains(chargeName)) return
            // no calculation if cost name is missing in list of costs to be calculated
            // no calculation if transport cost is not pre-calculated (which means error notification was already shown by transport accessorial cost)
            if (chargeName != "FREIGHT_CHARGE") {
                boolean isTCcalculated = false
                for (Object[] acCost : SUPP_COSTS) {
                    if (acCost) {
                        String costName = String.valueOf(acCost[0])
                        if (costName == "FREIGHT_CHARGE") {
                            isTCcalculated = true
                            break
                        }
                    }
                }
                if (!isTCcalculated) return
            }
        } else if (param.get("FT00443_HNDL_CHRG")) {
            unitType = 2
            if (!handlingCharges.contains(chargeName)) return
        } else return

        if (!param.get("FT1_ORIGIN_CTR") || !param.get("LOC99_CITY_ORIGIN") || !param.get("FT2_DEST_CTR") || !param.get("LOC99_CITY_DEST")) {
            ERR_MESSAGE = "Unit does not contain all shipment's mandatory data: origin country and city and destination county and city."
            return
        }

        /** STEP 2. Get unit's properties. **/

        String origCountryF = String.valueOf(param.get("FT1_ORIGIN_CTR")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String origCityF = param.get("MS002x_AREAS_ZIPS") ? String.valueOf(((Map) param.get("MS002x_AREAS_ZIPS")).get("FROM")).replaceAll("[^A-Za-z0-9]", "").toLowerCase() : ""
        String destCountryF = String.valueOf(param.get("FT2_DEST_CTR")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String destCityF = param.get("MS002x_AREAS_ZIPS") ? String.valueOf(((Map) param.get("MS002x_AREAS_ZIPS")).get("TO")).replaceAll("[^A-Za-z0-9]", "").toLowerCase() : ""
        Date shipDateF = Date.parse("yyyyMMdd", String.valueOf(param.get("LOC_SHIP_DATE")))
        String serviceF = String.valueOf(param.get("SERVICE")) == "STANDARD" ? "RSM1" : "RSM2"

        // define equipment type and commodity type (hazardous goods or not) based on CONT_LOAD
        String contLoad = String.valueOf(param.get("EQUIPMENT_TYPE"))
        boolean isHazmatF
        String equipTypeF
        switch (contLoad) {
            case "2D/20' FT": isHazmatF = false; equipTypeF = "20standarddry"; break
            case "4D/40' FT": isHazmatF = false; equipTypeF = "40standarddry"; break
            case "4H/40' HC": isHazmatF = false; equipTypeF = "40highcubedry"; break
            case "2F/20' REF": isHazmatF = false; equipTypeF = "20reefer"; break
            case "4F/40' REF": isHazmatF = false; equipTypeF = "40reefer"; break
            case "4F/40' HC REF": isHazmatF = false; equipTypeF = "40highcubereefer"; break
            case "2D/20' IMO": isHazmatF = true; equipTypeF = "20standarddry"; break
            case "4D/40' IMO": isHazmatF = true; equipTypeF = "40standarddry"; break
            case "4H/40' HC IMO": isHazmatF = true; equipTypeF = "40highcubedry"; break
            case "2F/20' REF IMO": isHazmatF = true; equipTypeF = "20reefer"; break
            case "4F/40' REF IMO": isHazmatF = true; equipTypeF = "40reefer"; break
            case "4F/40' HC REF IMO": isHazmatF = true; equipTypeF = "40highcubereefer"; break
            default: ERR_MESSAGE = "Unknown FRED container type."; return
        }

        /** STEP 3. Get and validate rates from data file. **/

        // get rate table from data file
        String[][] data = param.get("DATA")
        // create map with column indexes by header name; allows user randomize columns order in uploaded rate table
        Map<String, Integer> headerIndexes = new HashMap<String, Integer>()
        for (int i = 0; i < data[0].length; i++) {
            headerIndexes.put((data[0][i]).replaceAll("[^A-Za-z0-9]", "").toLowerCase(), i)
        }

        def mandatoryHeaders = ['preferredservice', 'validfrom', 'validfrom', 'origincountry', 'origincity', 'originservice',
                                'destinationcountry', 'destinationcity', 'destinationservice', 'commodity', 'equipmenttype',
                                'baserate', 'baseratecurrency']
        String missingHeaders
        for (String header : mandatoryHeaders) {
            if (!headerIndexes.containsKey(header)) {
                if (!missingHeaders) missingHeaders = "\n  - " + header
                else missingHeaders += "\n  - " + header
                missingHeaders += "\n"
            }
        }
        if (missingHeaders) {
            ERR_MESSAGE = "Format error.\nThere are missing mandatory columns with below listed header names in the uploaded rate table:" + missingHeaders
            return
        }

        /** STEP 4. Loop through rate table looking for matched quotation. **/

        ArrayList<Integer> validMatchedRows, nonValidMatchedRows, rowsWithMatchedCities, rowsWithMatchedCountries

        for (int n = 0; n < 2; n++) {
            for (int i = 1; i < data.length; i++) {
                int matchType = 0
                String origCountryR = data[i][headerIndexes.get("origincountry")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                String destCountryR = data[i][headerIndexes.get("destinationcountry")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                if (!origCountryR || !destCountryR || origCountryR == "" || destCountryR == "") {
                    ERR_MESSAGE = "Format error.\nRate table contains lane(s) with missing countries or specified in incorrect format. Please check rate table."
                    return
                }
                if (origCountryR == origCountryF && destCountryR == destCountryF) {
                    matchType = 1
                    String origCityR = data[i][headerIndexes.get("origincity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                    String destCityR = data[i][headerIndexes.get("destinationcity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                    if (((origCityR && origCityR != "" && origCityR == origCityF) || (!origCityR || origCityR == "")) &&
                            ((destCityR && destCityR != "" && destCityR == destCityF) || (!destCityR || destCityR == ""))) {
                        matchType = 2
                        String serviceR = data[i][headerIndexes.get("preferredservice")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                        String equipTypeR = data[i][headerIndexes.get("equipmenttype")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                        String commodityR = data[i][headerIndexes.get("commodity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                        if (serviceF == serviceR && equipTypeF == equipTypeR && ((!isHazmatF && commodityR.contains("nonhaz")) || (isHazmatF && !commodityR.contains("nonhaz")))) {
                            matchType = 3
                            String dateFormat = data[i][headerIndexes.get("validfrom")].contains("/") ? "MM/dd/yyyy" : "dd.MM.yyyy"
                            try {
                                Date validFrR = Date.parse(dateFormat, data[i][headerIndexes.get("validfrom")].trim())
                                Date validToR = Date.parse(dateFormat, data[i][headerIndexes.get("validto")].trim())
                                if (!shipDateF.before(validFrR) && !shipDateF.after(validToR)) {
                                    if (!validMatchedRows) validMatchedRows = new ArrayList<Integer>()
                                    validMatchedRows.add(i)
                                }
                            } catch (Exception e) {
                                ERR_MESSAGE = "Format error.\nQuotation(s) is found, however, validity dates have invalid format. Please check rate table."
                                return
                            }
                        }
                    }
                }

                if ((unitType == 1 && chargeName == "FREIGHT_CHARGE") || (unitType == 2 && (chargeName == "Destination Terminal Handling Charge"))) {
                    switch (matchType) {
                        case 0: break
                        case 1: if (!rowsWithMatchedCountries) rowsWithMatchedCountries = new ArrayList<Integer>(); rowsWithMatchedCountries.add(i); break
                        case 2: if (!rowsWithMatchedCities) rowsWithMatchedCities = new ArrayList<Integer>(); rowsWithMatchedCities.add(i); break
                        case 3: if (!nonValidMatchedRows) nonValidMatchedRows = new ArrayList<Integer>(); nonValidMatchedRows.add(i); break
                    }
                }
            }
            if (destCountryF == 'es' && destCityF == 'cabanillas') destCityF = 'valencia'
            else if (origCityF == 'dubai') origCityF = 'jebelali'
            else if (destCityF == 'dubai') destCityF = 'jebelali'
            else break
        }

        /** STEP 5. Validate and return found price. **/

        if (validMatchedRows && validMatchedRows.size() == 1) {
            int quotationIndex = validMatchedRows.get(0)
            // check whether ocean freight or surcharge should be calculated
            if (chargeName == "Transport cost") {
                // adding ocean freight to separate block because unlike surcharges it can be calculated with one of two possible rates (base or all-in):
                DT_CURRENCY_CODE = data[quotationIndex][headerIndexes.get("baseratecurrency")]
                try {
                    // 1.1.1 Ocean freight rate has valid format.
                    String baseRate = data[quotationIndex][headerIndexes.get("baserate")]
                    if (baseRate && baseRate.trim() != "") {
                        return R_VALUE_CALC = new BigDecimal(baseRate).setScale(2, RoundingMode.HALF_UP)
                    } else if (!headerIndexes.get("allinrate")) {
                        ERR_MESSAGE = "Format error.\nQuotation is found, however, ocean freight rate is missing. Please check rate table."
                        return
                    } else {
                        String allInRate = data[quotationIndex][headerIndexes.get("allinrate")]
                        if (allInRate && allInRate.trim() != "") {
                            return R_VALUE_CALC = new BigDecimal(allInRate).setScale(2, RoundingMode.HALF_UP)
                        } else {
                            ERR_MESSAGE = "Format error.\nQuotation is found, however, ocean freight rate is missing. Please check rate table."
                            return
                        }
                    }
                } catch (NumberFormatException e) {
                    // 1.1.2 Ocean freight rate is missing or has invalid format.
                    ERR_MESSAGE = "Format error.\nQuotation is found, however, ocean freight rate has invalid format. Please check rate table."
                    return
                }
            } else {
                // if calculation cost is not ocean freight,
                // check if the cost is applicable (depending on origin/destination service):
                String origServiceRS = data[quotationIndex][headerIndexes.get("originservice")]
                String destServiceRS = data[quotationIndex][headerIndexes.get("destinationservice")]
                if ((unitType == 1 && origServiceRS == 'D') || (unitType == 2 && destServiceRS == 'D')) {
                    // if accessorial cost is paid at origin and origin service is 'from-door' - calculate cost
                    // if accessorial cost is paid at destination and destination service is 'to-door' - calculate cost
                    DT_CURRENCY_CODE = data[quotationIndex][headerIndexes.get(chargeName.replaceAll("[^A-Za-z0-9]", "").toLowerCase() + "currency")]
                    try {
                        // 1.1.1 Charge's rate has valid format.
                        String rate = data[quotationIndex][headerIndexes.get(chargeName.replaceAll("[^A-Za-z0-9]", "").toLowerCase() + "value")]
                        if (rate && rate.trim() != "") {
                            return R_VALUE_CALC = new BigDecimal(rate).setScale(2, RoundingMode.HALF_UP)
                        } else {
                            // if no rate available for the charge - then no cost calculation.
                            return R_VALUE_CALC
                        }
                    } catch (NumberFormatException e) {
                        // 1.1.2 Charge's rate is has invalid format.
                        ERR_MESSAGE = "Format error.\n" + chargeName.toString() + " rate has invalid format. Please check rate table."
                        return
                    }
                } else if ((unitType == 1 && origServiceRS == 'P') || (unitType == 2 && destServiceRS == 'P'))
                // if accessorial cost is paid at origin and origin service is 'from-port' - do not calculate cost
                // if accessorial cost is paid at destination and destination service is 'to-port' - do not calculate cost
                    return R_VALUE_CALC
                else {
                    // if origin/destination service is empty or has other value than 'D' or 'P' - show error notification
                    ERR_MESSAGE = "Origin or destination service is not indicated or has invalid format."
                    return
                }
            }
        } else if (unitType == 2 && chargeName != "Destination Terminal Handling Charge") return

        /** STEP 6. Show corresponding notification otherwise. **/

        // declare reference to one of created above array lists which will be used for error notification
        ArrayList<Integer> messageList

        if (validMatchedRows && validMatchedRows.size() > 1) {
            // 2. Found more than one matched quotation (MCF).
            ERR_MESSAGE = "Multiple quotations are found.\nAvailable are " + validMatchedRows.toString() + ":"
            messageList = validMatchedRows
        } else if (nonValidMatchedRows) {
            // 1.2 Quotation is not applicable for the shipment (out of validity period).
            if (nonValidMatchedRows.size() == 1) {
                // One matching quotation out of validity period.
                ERR_MESSAGE = "Quotation is found, however, with out of bound validity period:"
            } else if (nonValidMatchedRows.size() > 1) {
                // More than one matching quotation out of validity period.
                ERR_MESSAGE = "Several quotations are found, however, with out of bound validity periods:"
            }
            messageList = nonValidMatchedRows
        } else if (rowsWithMatchedCities) {
            ERR_MESSAGE = "Quotation not found.\nAvailable quotations for lane with specified cities:"
            messageList = rowsWithMatchedCities
        } else if (rowsWithMatchedCountries) {
            ERR_MESSAGE = "Quotation not found.\nAvailable quotations for lane with specified countries:"
            messageList = rowsWithMatchedCountries
        } else {
            ERR_MESSAGE = "Quotation not found.\nNo similar quotations are available."
            return
        }

        def list = '\n\n' << ''
        if (messageList == rowsWithMatchedCities || messageList == nonValidMatchedRows || messageList == validMatchedRows) {
            for (Integer i : messageList) {
                list << data[i][headerIndexes.get("preferredservice")].trim() << "  " <<
                        data[i][headerIndexes.get("commodity")].replaceAll(" ", "") << "  " <<
                        data[i][headerIndexes.get("equipmenttype")].replaceAll(" ", "") << "  " <<
                        data[i][headerIndexes.get("validfrom")].trim() << "-" <<
                        data[i][headerIndexes.get("validto")].trim() << "\n"
            }
        } else {
            for (Integer i : messageList) {
                list << data[i][headerIndexes.get("preferredservice")].trim() << "  " <<
                        data[i][headerIndexes.get("commodity")].replaceAll(" ", "") << "  " <<
                        data[i][headerIndexes.get("equipmenttype")].replaceAll(" ", "") << "  " <<
                        data[i][headerIndexes.get("validfrom")].trim() << "-" <<
                        data[i][headerIndexes.get("validto")].trim() << "\n" <<
                        data[i][headerIndexes.get("origincountry")].trim() << "  " <<
                        data[i][headerIndexes.get("origincity")].trim() << "  -  " <<
                        data[i][headerIndexes.get("destinationcountry")].trim() << "  " <<
                        data[i][headerIndexes.get("destinationcity")].trim() << "\n"
            }
        }

        ERR_MESSAGE += list
        return
    }
}