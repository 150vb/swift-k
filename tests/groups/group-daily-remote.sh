# GROUPLIST definition to run all local tests

GROUPLIST=( $TESTDIR/language/working \
            $TESTDIR/local \
            $TESTDIR/language/should-not-work \
            # $TESTDIR/cdm \
            # $TESTDIR/cdm/ps \
            # $TESTDIR/cdm/star
            $TESTDIR/language-behaviour/arrays \
            $TESTDIR/language-behaviour/broken \
	        $TESTDIR/language-behaviour/compounds \
            $TESTDIR/language-behaviour/control_structures \
            $TESTDIR/language-behaviour/datatypes \
	        $TESTDIR/language-behaviour/IO \
	        $TESTDIR/language-behaviour/logic \
	        $TESTDIR/language-behaviour/mappers \
	        $TESTDIR/language-behaviour/math \
	        $TESTDIR/language-behaviour/params \
            $TESTDIR/language-behaviour/procedures \
            $TESTDIR/language-behaviour/strings \
	        $TESTDIR/language-behaviour/variables \
	        $TESTDIR/language-behaviour/cleanup \
      	    $TESTDIR/bugs \
	        $TESTDIR/documentation/tutorial \
            $TESTDIR/functions \

            # Site testing test-group
            $TESTDIR/sites/beagle \
            $TESTDIR/sites/mcs    \
            $TESTDIR/sites/midway \
            $TESTDIR/sites/uc3    \
	        # Frisbee will fail due to Bug 1030
            $TESTDIR/sites/mac-frisbee  \
            $TESTDIR/sites/blues  \
            $TESTDIR/sites/fusion \
            $TESTDIR/sites/raven  \

 	        # Remote-cluster IO tests
	        $TESTDIR/stress/IO/beagle \
            $TESTDIR/stress/IO/bagOnodes \
            $TESTDIR/stress/IO/multiple \
            $TESTDIR/stress/IO/uc3 \

            # Language stress tests
            $TESTDIR/stress/internals \

	        # Remote-cluster Apps tests - MODIS
            $TESTDIR/stress/apps/modis_beagle  \
            $TESTDIR/stress/apps/modis_local   \
	        $TESTDIR/stress/apps/modis_midway  \
	        $TESTDIR/stress/apps/modis_uc3     \
            # $TESTDIR/stress/apps/modis_multiple\

            # Local stress tests
            $TESTDIR/stress/internals \
            # Local cluster tests.
            $TESTDIR/stress/local_cluster \
            $TESTDIR/stress/random_fail \
            $TESTDIR/stress/jobs_per_node \

       	    # Recursive Test invocation
	        $TESTDIR/multi_remote

          )

checkvars WORK
