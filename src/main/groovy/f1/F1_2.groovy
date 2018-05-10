package f1

class F1_2 {
    static def DT_CURRENCY_CODE
    static def R_VALUE_CALC
    static def ERR_MESSAGE
    static Object[][] SUPP_COSTS

    static String getResult(Map<String, Object> param) {
        R_VALUE_CALC = calculate(param)
        if (!R_VALUE_CALC) {
            return ERR_MESSAGE
        }else
            return "CALC_RES | " + R_VALUE_CALC + " " + DT_CURRENCY_CODE
    }

    static BigDecimal calculate(Map<String, Object> param) {
        DT_CURRENCY_CODE = "USD"
        R_VALUE_CALC = BigDecimal.ZERO

        /** STEP 1. Validate. **/

        if (param.get("ID228_FR_UNIT")) {
            boolean isTCcalculated = false
            for (Object[] acCost : SUPP_COSTS) {
                String costName = (String) acCost[0]
                if (costName == "FREIGHT_CHARGE") isTCcalculated = true
            }
            if (!isTCcalculated) return
        }else return

        /** STEP 2. Get properties. **/

        String origCountryF = String.valueOf(param.get("FT1_ORIGIN_CTR")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String origCityF = String.valueOf(param.get("LOC99_CITY_ORIGIN")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String destCountryF = String.valueOf(param.get("FT2_DEST_CTR")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String destCityF = String.valueOf(param.get("LOC99_CITY_DEST")).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        Date shipDateF = Date.parse("yyyyMMdd", String.valueOf(param.get("SHP_DATE_LOC")))
        String serviceF = String.valueOf(param.get("SERVICE")) == "STANDARD" ? "RSM1" : "RSM2"

        // define equipment type and commodity type (hazardous goods or not) based on CONT_LOAD
        String contLoad = String.valueOf(param.get("CONT_LOAD"))
        boolean isHazmatF = true
        String equipTypeF = "DEFAULT"

        /** STEP 3. Get and validate rates from data file. **/

        // get rate table from data file
        String[][] data = param.get("DATA")
        // create map with column indexes by header name; allows user randomize columns order in uploaded rate table
        Map<String, Integer> headerIndexes = new HashMap<String, Integer>()
        for (int i = 0; i < data[0].length ; i++) {
            headerIndexes.put((data[0][i]).replaceAll("[^A-Za-z0-9]", "").toLowerCase(), i)
        }

        def mandatoryHeaders = ['preferredservice','validfrom','validfrom','origincountry','origincity','originservice',
                                'destinationcountry','destinationcity','destinationservice','commodity','equipmenttype',
                                'baserate','baseratecurrency','originregion','destinationregion']
        String missingHeaders
        for (String header : mandatoryHeaders) {
            if (!headerIndexes.containsKey(header)) {
                if (!missingHeaders) missingHeaders = "\n  - " + header
                else missingHeaders += "\n  - " + header
            }
        }
        if (missingHeaders) {
            ERR_MESSAGE = "Format error.\nThere are missing mandatory columns with below listed header names in the uploaded rate table:" + missingHeaders
            return
        }

        /** STEP 4. Loop through rate table looking for matched quotation. **/

        int quotationIndex
        for (int i = 1; i < data.length; i++) {
            String origCountryR = data[i][headerIndexes.get("origincountry")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
            String destCountryR = data[i][headerIndexes.get("destinationcountry")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
            if (origCountryR == origCountryF && destCountryR == destCountryF) {
                String origCityR = data[i][headerIndexes.get("origincity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                String destCityR = data[i][headerIndexes.get("destinationcity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                if (((origCityR && origCityR != "" && origCityR == origCityF) || (!origCityR || origCityR == "")) &&
                        ((destCityR && destCityR != "" && destCityR == destCityF) || (!destCityR || destCityR == ""))) {
                    String serviceR = data[i][headerIndexes.get("preferredservice")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                    String equipTypeR = data[i][headerIndexes.get("equipmenttype")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                    String commodityR = data[i][headerIndexes.get("commodity")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                    if (serviceF == serviceR && equipTypeF == equipTypeR && ((!isHazmatF && commodityR.contains("nonhaz")) || (isHazmatF && !commodityR.contains("nonhaz")))) {
                        String dateFormat = data[i][headerIndexes.get("validfrom")].contains("/") ? "MM/dd/yyyy" : "dd.MM.yyyy"
                        Date validFrR = Date.parse(dateFormat, data[i][headerIndexes.get("validfrom")].trim())
                        Date validToR = Date.parse(dateFormat, data[i][headerIndexes.get("validto")].trim())
                        if (!shipDateF.before(validFrR) && !shipDateF.after(validToR)) {
                            quotationIndex = i
                            break
                        }
                    }
                }
            }
        }

        // No calculation if base rate is not available which means 'all-in rate' was applied for transport cost
        // calculation, thus f1.F1_2 should not be calculated
        String baseRate = data[quotationIndex][headerIndexes.get("baserate")]
        if (!baseRate && baseRate.trim() == "") return R_VALUE_CALC

        /** STEP 5. Loop through f1.F1_2 rate table looking for corresponding rate. **/

        String origRegion = data[quotationIndex][headerIndexes.get("originregion")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()
        String destRegion = data[quotationIndex][headerIndexes.get("destinationregion")].replaceAll("[^A-Za-z0-9]", "").toLowerCase()

        int supplmHeader
        for (int i = quotationIndex; i < data.length; i++) {
            if (data[i][0] == "SUPPLEMENT") supplmHeader = i
        }

        if (!supplmHeader) return

        String contType
        if (equipTypeF.contains("20")) contType = "20"
        if (equipTypeF.contains("highcube")) contType = "40hc"
        else contType = "40"


        String bafLane = origRegion + "to" + destRegion

        int rateRow, rateCol

        for (int row = supplmHeader + 2; row < data.length; row++) {
            if (bafLane == data[row][0].replaceAll("[^A-Za-z0-9]", "").toLowerCase())
                rateRow = row
        }
        if (rateRow) {
            String quarter = "q"+(String.valueOf((int)(shipDateF[Calendar.MONTH] / 3) + 1) + String.valueOf(shipDateF[Calendar.YEAR])).replaceAll("[^A-Za-z0-9]", "").toLowerCase()
            for (int col = data[supplmHeader].length - 1; col > 1; col--) {
                if (contType == data[supplmHeader + 1][col].replaceAll("[^A-Za-z0-9]", "").toLowerCase() &&
                        quarter == data[supplmHeader][col].replaceAll("[^A-Za-z0-9]", "").toLowerCase())
                    rateCol = col
            }
            if (!rateCol) {
                for (int col = data[supplmHeader].length - 1; col > 1; col--) {
                    String cellValue = data[supplmHeader][col].toLowerCase()
                    if (!cellValue.startsWith("q") &&
                            contType == data[supplmHeader + 1][col].replaceAll("[^A-Za-z0-9]", "").toLowerCase()) {

                        String[] dates = cellValue.split("-")
                        String dateFormat = dates[0].contains("/") ? "MM/dd/yyyy" : "dd.MM.yyyy"
                        Date validFrom = Date.parse(dateFormat, dates[0].trim())

                        if (!shipDateF.before(validFrom)) {
                            Date validTo = Date.parse(dateFormat, dates[1].trim())
                            if (!shipDateF.after(validTo))
                                rateCol = col
                        }
                    }
                }
            }
        }

        if (rateRow && rateCol) {
            try {
                return R_VALUE_CALC = new BigDecimal(data[rateRow][rateCol].trim().replaceAll('\\$',''))
            } catch (NumberFormatException e) {
                ERR_MESSAGE = "Found f1.F1_2 rate has invalid format. Please check f1.F1_2 rates."
                return
            }
        }else {
            if (!rateRow) ERR_MESSAGE = "Missing corresponding trade lane in f1.F1_2 rate table."
            else ERR_MESSAGE = "Missing valid f1.F1_2 rates."
            return
        }
    }
}































