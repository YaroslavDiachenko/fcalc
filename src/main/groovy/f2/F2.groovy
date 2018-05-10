package f2

import java.math.RoundingMode


class F2 {
    static def DT_CURRENCY_CODE
    static def R_VALUE_CALC
    static def ERR_MESSAGE

    static String getResult(Map<String, Object> param) {
        R_VALUE_CALC = calculate(param)
        if (!R_VALUE_CALC) {
            return ERR_MESSAGE
        }else
            return "CALC_RES | " + R_VALUE_CALC + " " + DT_CURRENCY_CODE
    }

    static String calculate(Map<String, Object> param) {
        DT_CURRENCY_CODE = "EUR"
        R_VALUE_CALC = BigDecimal.ZERO

        String[] specialZips = ['6236', '5465', '9326', '2367']

        String[][] data = (String[][]) param.get("data")
        if (data == null) {
            ERR_MESSAGE = "Formula error. Cannot retrieve rates from data file."
            return
        }

        BigDecimal weight = param.get("DT00_CHARG_W_KG") ? new BigDecimal(String.valueOf(param.get("DT00_CHARG_W_KG"))) : BigDecimal.ZERO
        String origCountry = param.get('FT1_ORIGIN_CTR')
        String destCountry = param.get('FT2_DEST_CTR')

        if (param.get("MS002x_AREAS_ZIPS") == null) return

        String origZone = String.valueOf(((Map)param.get("MS002x_AREAS_ZIPS")).get("FROM"))
        String destZone = String.valueOf(((Map)param.get("MS002x_AREAS_ZIPS")).get("TO"))

        String origZip = param.get('PP001_ZIP_ORIGIN')
        String destZip = param.get('PP002_ZIP_DEST')

        String load = param.get('LOAD')
        String contLoad = param.get('EQUIP')
        String ie = param.get('BU')

        // define rate card name
        String rateCardName = ''
        if (origZone == 'PL-01' && destZone == 'FI-02' && ie == 'AB06')
            rateCardName = 'RATE_SHEET_AA23FN1'
        else if (destZip == '58708' && origZone == 'FI-02' && destZone == 'DE-05' && load == 'FTL/FCL')
            rateCardName = 'RATE_SHEET_HV17MM1'
        else if (String.valueOf(param.get('SHIPMENT_ID')).startsWith('HELAB') && ie == 'AB01' && origZone == 'FI-02')
            rateCardName = 'RATE_SHEET_AN12FQ1'
        else if ((origCountry == 'FI' && destCountry == 'EE') || (origCountry == 'EE' && destCountry == 'FI')) {
            if ((ie == 'AB05' && ((origZone == 'FI-01' && destZone == 'EE-01') || (origZone == 'EE-01' && destZone == 'FI-01'))) ||
                    (ie == 'AB01' && ((origZone == 'FI-02' && destZone == 'EE-02') || (origZone == 'EE-02' && destZone == 'FI-02')))) {
                if ((origCountry == 'EE' && origZip in specialZips && param.get('LOADING_LOCATION') && param.get('LOADING_LOCATION') == origZip) ||
                        (destCountry == 'EE' && destZip in specialZips && param.get('LOADING_LOCATION') && param.get('LOADING_LOCATION') == destZip))
                    rateCardName = 'RATE_SHEET_BC88FF2'
            }
            if (rateCardName == '') {
                if (((origCountry == 'FI' && origZone == 'FI-01') || (destCountry == 'FI' && destZone == 'FI-01')) &&
                        ie in ['AB05', 'AB08', 'AB18'])
                    rateCardName = 'RATE_SHEET_GK983M25'
                else
                    rateCardName = 'RATE_SHEET_GO912RT1'
            }
        }else
            rateCardName = 'RATE_SHEET_DEFAULT'

        for (int i = 0; i < data.length; i++) {
            // find in data file rate table for corresponding rate card name
            if (data[i][0] == 'Ratecard') {
                if (data[i][1].equalsIgnoreCase(rateCardName)) {

                    // remember row index of the header of applicable rate table from data file
                    int headerRow = i + 1

                    // find rateline for shipment's origin and destination zones
                    for (int j = headerRow + 1; j < data.length; j++) {
                        if (origCountry == data[j][0] && destCountry == data[j][2]) {
                            if (origZone == data[j][1] && destZone == data[j][3]) {

                                // remember row index of the defined rate line of applicable rate table from data file
                                int ratesRow = j

                                // find applicable rate
                                if (load == 'NOT_FULL') {
                                    for (int k = 4; k < data[ratesRow].length; k++) {
                                        if (data[headerRow][k].isNumber()) {
                                            BigDecimal wBrCeil = new BigDecimal(data[headerRow][k])
                                            if (weight.compareTo(wBrCeil) < 1) {
                                                String wBr = '<=' + wBrCeil
                                                BigDecimal rate = new BigDecimal(data[ratesRow][k])
                                                BigDecimal cost = rate.multiply(weight)

                                                // compare calculated cost to next weight bracket according to minimum calculation rule
                                                if (data[headerRow][k].isNumber()) {
                                                    BigDecimal nextwBrMinWeight = new BigDecimal(data[headerRow][k]).add(1)
                                                    BigDecimal nextwBrRate = new BigDecimal(data[ratesRow][k+1])
                                                    BigDecimal minRuleCost = nextwBrMinWeight.multiply(nextwBrRate)
                                                    if (minRuleCost.compareTo(cost) == -1) {
                                                        cost = minRuleCost
                                                        weight = nextwBrMinWeight
                                                        wBr = '<=' + data[headerRow][k+1]
                                                        rate = nextwBrRate
                                                    }
                                                }

                                                // compare calculated cost to minimum charge and maximum charge (if available)
                                                for (int n = 0; n < data[headerRow].length; n++) {
                                                    if (data[headerRow][n] == 'Min') {
                                                        BigDecimal min = new BigDecimal(data[ratesRow][n])
                                                        if (cost.compareTo(min) == -1) {
                                                            cost = min
                                                            wBr = 'Min'
                                                        }
                                                    }else if (data[headerRow][n] == 'Max') {
                                                        BigDecimal max = new BigDecimal(data[ratesRow][n])
                                                        if (cost.compareTo(max) == 1) {
                                                            cost = max
                                                            wBr = 'Max'
                                                        }
                                                    }
                                                }

                                                // if value 'test' in order tag 'X_DOCKING_LOCATION' then show applicable data
                                                if (param.get('X_DOCKING_LOCATION') && param.get('X_DOCKING_LOCATION') == 'info') {
                                                    String sb = 'Rate card name:  '<<rateCardName<<'\n'<<
                                                            'Calculation rule type:  Minimum'<<'\n'<<
                                                            'Measurement unit:  Weight-chargeable kg'<<'\n'<<
                                                            'Quantity of units:  '<<weight<<'\n'<<
                                                            'Bracket:  '<<wBr<<'\n'<<
                                                            'Rate:  '<<rate<<' '<<DT_CURRENCY_CODE<<' / kg'<<'\n'<<
                                                            'Calculated cost:  '<<cost.setScale(2,RoundingMode.HALF_UP)<<' '<<DT_CURRENCY_CODE
                                                    ERR_MESSAGE = sb
                                                    return
                                                }

                                                // otherwise return calculated cost
                                                else {
                                                    return R_VALUE_CALC = cost.setScale(2, RoundingMode.HALF_UP)
                                                }
                                            }
                                        }
                                    }
                                }else if (load == 'FULL') {
                                    for (int m = data[headerRow].length - 1; m > 0; m--) {
                                        if (contLoad.split('/')[1].trim().equalsIgnoreCase(data[headerRow][m].split('-')[1].trim())) {
                                            BigDecimal cost = new BigDecimal(data[ratesRow][m])

                                            // if value 'test' in order tag 'X_DOCKING_LOCATION' then show applicable data
                                            if (param.get('X_DOCKING_LOCATION') && param.get('X_DOCKING_LOCATION') == 'info') {
                                                String sb = 'Rate card name:  '<<rateCardName<<'\n'<<
                                                        'Equipment type:  '<<data[headerRow][m]<<'\n'<<
                                                        'Rate:  '<<cost<<' '<<DT_CURRENCY_CODE
                                                ERR_MESSAGE = sb
                                                return
                                            }

                                            // otherwise return calculated cost
                                            else {
                                                return R_VALUE_CALC = cost.setScale(2, RoundingMode.HALF_UP)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ERR_MESSAGE = 'Formula error. Corresponding rates are not found in data file.'
        return
    }
}
