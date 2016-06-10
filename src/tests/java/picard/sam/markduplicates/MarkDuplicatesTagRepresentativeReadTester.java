/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.sam.markduplicates;

import htsjdk.samtools.DuplicateScoringStrategy;
import htsjdk.samtools.DuplicateScoringStrategy.ScoringStrategy;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import picard.cmdline.CommandLineProgram;
import picard.sam.DuplicationMetrics;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is an extension of AbstractMarkDuplicatesCommandLineProgramTester used to test MarkDuplicatesWithMateCigar with SAM files generated on the fly.
 * This performs the underlying tests defined by classes such as see AbstractMarkDuplicatesCommandLineProgramTest and MarkDuplicatesWithMateCigarTest.
 */
public class MarkDuplicatesTagRepresentativeReadTester extends AbstractMarkDuplicatesCommandLineProgramTester {

    final public Map<String, String> readToRepReadMap = new HashMap<>(); // map to contain representative read names for each record
    final public Map<String, Integer> expectedSetSizeMap = new HashMap<>();
    public boolean testRepresentativeReads = false;

    public MarkDuplicatesTagRepresentativeReadTester() {
        super(DuplicateScoringStrategy.ScoringStrategy.SUM_OF_BASE_QUALITIES);
        //super(DuplicateScoringStrategy.ScoringStrategy.TOTAL_MAPPED_REFERENCE_LENGTH);

        addArg("TAGGING_POLICY=All");
        addArg("TAG_REPRESENTATIVE_READ=true");
    }

    @Override
    protected CommandLineProgram getProgram() { return new MarkDuplicates(); }

    @Override
    public void test() {
        try {
            updateExpectedDuplicationMetrics();

            // Read the output and check the duplicate flag
            int outputRecords = 0;
            final SamReader reader = SamReaderFactory.makeDefault().open(getOutput());
            for (final SAMRecord record : reader) {
                outputRecords++;
                final String key = samRecordToDuplicatesFlagsKey(record);
                if (!this.duplicateFlags.containsKey(key)) {
                    System.err.println("DOES NOT CONTAIN KEY: " + key);
                }
                Assert.assertTrue(this.duplicateFlags.containsKey(key));
                final boolean value = this.duplicateFlags.get(key);
                this.duplicateFlags.remove(key);
                if (value != record.getDuplicateReadFlag()) {
                    System.err.println("Mismatching read:");
                    System.err.print(record.getSAMString());
                }
                Assert.assertEquals(record.getDuplicateReadFlag(), value);
                if (testRepresentativeReads) {
                    Assert.assertEquals(record.getAttribute("RR"), readToRepReadMap.get(record.getReadName()));
                    Assert.assertEquals(record.getAttribute("DS"), expectedSetSizeMap.get(record.getReadName()));
                }
            }
            CloserUtil.close(reader);

            // Ensure the program output the same number of records as were read in
            Assert.assertEquals(outputRecords, this.getNumberOfRecords(), ("saw " + outputRecords + " output records, vs. " + this.getNumberOfRecords() + " input records"));

            // Check the values written to metrics.txt against our input expectations
            final MetricsFile<DuplicationMetrics, Comparable<?>> metricsOutput = new MetricsFile<DuplicationMetrics, Comparable<?>>();
            try{
                metricsOutput.read(new FileReader(metricsFile));
            }
            catch (final FileNotFoundException ex) {
                System.err.println("Metrics file not found: " + ex);
            }
            Assert.assertEquals(metricsOutput.getMetrics().size(), 1);
            final DuplicationMetrics observedMetrics = metricsOutput.getMetrics().get(0);
            Assert.assertEquals(observedMetrics.UNPAIRED_READS_EXAMINED, expectedMetrics.UNPAIRED_READS_EXAMINED, "UNPAIRED_READS_EXAMINED does not match expected");
            Assert.assertEquals(observedMetrics.READ_PAIRS_EXAMINED, expectedMetrics.READ_PAIRS_EXAMINED, "READ_PAIRS_EXAMINED does not match expected");
            Assert.assertEquals(observedMetrics.UNMAPPED_READS, expectedMetrics.UNMAPPED_READS, "UNMAPPED_READS does not match expected");
            Assert.assertEquals(observedMetrics.UNPAIRED_READ_DUPLICATES, expectedMetrics.UNPAIRED_READ_DUPLICATES, "UNPAIRED_READ_DUPLICATES does not match expected");
            Assert.assertEquals(observedMetrics.READ_PAIR_DUPLICATES, expectedMetrics.READ_PAIR_DUPLICATES, "READ_PAIR_DUPLICATES does not match expected");
            Assert.assertEquals(observedMetrics.READ_PAIR_OPTICAL_DUPLICATES, expectedMetrics.READ_PAIR_OPTICAL_DUPLICATES, "READ_PAIR_OPTICAL_DUPLICATES does not match expected");
            Assert.assertEquals(observedMetrics.PERCENT_DUPLICATION, expectedMetrics.PERCENT_DUPLICATION, "PERCENT_DUPLICATION does not match expected");
            Assert.assertEquals(observedMetrics.ESTIMATED_LIBRARY_SIZE, expectedMetrics.ESTIMATED_LIBRARY_SIZE, "ESTIMATED_LIBRARY_SIZE does not match expected");
            Assert.assertEquals(observedMetrics.SECONDARY_OR_SUPPLEMENTARY_RDS, expectedMetrics.SECONDARY_OR_SUPPLEMENTARY_RDS, "SECONDARY_OR_SUPPLEMENTARY_RDS does not match expected");
        } finally {
            TestUtil.recursiveDelete(getOutputDir());
        }
    }

}
