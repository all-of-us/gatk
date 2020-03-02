package org.broadinstitute.hellbender.tools.walkers.consensus;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.engine.DuplicateSetWalker;
import org.broadinstitute.hellbender.engine.FeatureContext;
import org.broadinstitute.hellbender.engine.ReferenceContext;
import org.broadinstitute.hellbender.tools.walkers.mutect.consensus.DuplicateSet;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.io.IOUtils;
import org.broadinstitute.hellbender.utils.read.SAMFileGATKReadWriter;
import picard.cmdline.programgroups.ReadDataManipulationProgramGroup;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


@CommandLineProgramProperties(
        summary = "asdf",
        oneLineSummary = "asdf",
        programGroup = ReadDataManipulationProgramGroup.class
)
/***
 * TODO: Can we support downsampling to a specified number of reads?
 * Perhaps take some metrics from fgbio (e.g. family size distribution) as input?
 * Want to avoid holding all reads in memory --- is there a way to do this?
 *
 * ***/
public class DownsampleByDuplicateSet extends DuplicateSetWalker {
    @Argument(fullName = StandardArgumentDefinitions.OUTPUT_LONG_NAME, shortName = StandardArgumentDefinitions.OUTPUT_SHORT_NAME, doc = "")
    public File outputBam;

    @Argument(fullName = "DS", doc = "This fraction of duplicate sets will be retained", optional=true)
    public double downsamplingRate = 1.0;

    @Argument(fullName = "keep-only-simplex", doc = "Only keep the simplex duplicate sets", optional=true)
    private boolean keepOnlySimplexSets = false; // TODO: default should be true by convention? TODO: What a stupid option!

    private static final int RANDOM_SEED = 142;
    private RandomGenerator rng;
    private static int numFragments;
    private static int numReads;
    private SAMFileGATKReadWriter outputWriter;


    @Override
    public void onTraversalStart() {
        super.onTraversalStart();
        rng = RandomGeneratorFactory.createRandomGenerator(new Random(RANDOM_SEED));
        outputWriter = createSAMWriter(IOUtils.getPath(outputBam.getAbsolutePath()), false);
    }

    @Override
    public void apply(DuplicateSet duplicateSet, ReferenceContext referenceContext, FeatureContext featureContext) {
        if (filterThisSet(duplicateSet)){
            return;
        }
        if (rng.nextDouble() > downsamplingRate){
            // TODO: test that the order is preserved
            duplicateSet.getReads().forEach(r -> outputWriter.addRead(r));
            numReads += duplicateSet.getReads().size();
            numFragments += 1;
        }
    }

    @Override
    public Object onTraversalSuccess(){
        outputWriter.close();
        logger.info(String.format("Wrote %d reads", numReads));
        logger.info(String.format("Wrote %d fragments", numFragments));
        return "SUCCESS";
    }

    private boolean filterThisSet(final DuplicateSet duplicateSet){
        final List<String> molecularIDs = duplicateSet.getReads().stream().map(r -> r.getAttributeAsString(DuplicateSet.FGBIO_MOLECULAR_IDENTIFIER_TAG))
                .distinct().collect(Collectors.toList());
        Utils.validate(0 < molecularIDs.size() && molecularIDs.size() <= 2, "Invalid molecularIDs: " + molecularIDs);
        return keepOnlySimplexSets && molecularIDs.size() == 2;
    }
}
