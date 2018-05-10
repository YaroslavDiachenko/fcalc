### Technical usage

Defining required rate in complex two dimensional table according to the specified parameters.
Returning message with similar contractual lanes in case of missing absolutely matched result.

### Calculation steps

Step 1. Validate.
Step 2. Get properties.
Step 3. Get and validate values from rate table.
Step 4. Validate and return found price.
Step 5. Show corresponding notification otherwise.

### Calculation scenarios

1.	Successful.
	1.1.	At least one cost is calculated.
	1.2.	No costs or only part of them are calculated.
	Possible reasons for not calculated cost(s) (in case of no error notification is shown):
	a.	Missing one of the following trigger elements:
		-	FT00443_FR_CHRG
		-	FT00237_CONTS
		-	FT00166_DOCS
		-	FT00166_PALLETS

	b.	Handling charges not calculated if destination service = ‘P’
	c.	Origin charges not calculated if origin service = ‘P’
	d.	Bunker charge not calculated if all-in rate is applicable All-in rate is applicable if base rate is missing for the found quotation in rate sheet.
2.	Unsuccessful.
	> Note:  Error meassage is triggered.

	2.1.	Quotation(s) found
	- A quotation is found, however, with out of bound validity period.
	- Found more than one matched quotation.
	- Found more than one matched quotation and all with out of bound validity periods.
		> Note:  With out of bound validity period means that shipment’s loading date is not covered by contractual validity period of found quotation.

	2.2.	Format error
	- Returned rate currency is not available among available currency codes.
	- Missing mandatory columns with below listed header names in the uploaded rate table.
	- Rate table contains lane(s) with missing countries or specified in incorrect format.
	- Quotation(s) is found, however, validity dates have invalid format.
	- Quotation is found, however, freight or surcharge has invalid format.
	- Quotation is found, however, ocean freight rate is missing.
		> Note:  Each quotation has to contain either base or all-in freight price.

	2.3.	Quotation not found
	- Quotation not found but quotations for lanes with same cities are available.
	- Quotation not found but quotations for lanes with same countries are available.
	- Quotation not found and no similar quotations are available in rate sheet.

### Technical requirements

1.	Uploaded rate table
	- Mandatory columns (with corresponding header names):
	According to rate table specification.
		> Note:  Columns’ order can be random.
	-	Dates formats:
		-	dd.MM.yyyy (e.g. 31.01.2018)
		-	MM/dd/yyyy (e.g. 1/31/2018)
		-	\<quarter> + \<year> (e.g. Q22018)
		-	\<valid from date> - \<valid to date> (e.g. 4/1/2018 – 6/30/2018)

2.	Formula.
	- Main formula specifics
	Is customized for calculation of different costs.
	In order to configure the formula to calculate specific cost, the contractual charge name should be specified at the beginning of the formula.
		> Note the specified in formula charge name (except transport cost) should be equal to its header name in rate sheet (ignore suffix "Value" or "Currency"). This is core requirement since it serves as a search key.
	- Supplement formula specifics
	By default calculates only supplement cost.
		> Note: Below uploaded main rate sheet a add rate table should be uploaded as well.