package org.kettle.beam.util;

import org.kettle.beam.metastore.FieldDefinition;
import org.kettle.beam.metastore.FileDefinition;
import org.kettle.beam.steps.io.BeamInputMeta;
import org.kettle.beam.steps.io.BeamOutputMeta;
import org.pentaho.di.core.Condition;
import org.pentaho.di.core.row.ValueMetaAndData;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.TransHopMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.constant.ConstantMeta;
import org.pentaho.di.trans.steps.dummytrans.DummyTransMeta;
import org.pentaho.di.trans.steps.filterrows.FilterRowsMeta;
import org.pentaho.di.trans.steps.memgroupby.MemoryGroupByMeta;
import org.pentaho.di.trans.steps.mergejoin.MergeJoinMeta;
import org.pentaho.di.trans.steps.streamlookup.StreamLookupMeta;
import org.pentaho.di.trans.steps.switchcase.SwitchCaseMeta;
import org.pentaho.di.trans.steps.switchcase.SwitchCaseTarget;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.metastore.persist.MetaStoreFactory;
import org.pentaho.metastore.util.PentahoDefaults;

import java.util.List;

public class BeamTransMetaUtil {

  public static final TransMeta generateBeamInputOutputTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the io step
    //
    BeamInputMeta beamInputMeta = new BeamInputMeta();
    beamInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    beamInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta beamInputStepMeta = new StepMeta(inputStepname, beamInputMeta);
    beamInputStepMeta.setStepID( "BeamInput" );
    transMeta.addStep( beamInputStepMeta );


    // Add a dummy in between to get started...
    //
    DummyTransMeta dummyTransMeta = new DummyTransMeta();
    StepMeta dummyStepMeta = new StepMeta("Dummy", dummyTransMeta);
    transMeta.addStep( dummyStepMeta );
    transMeta.addTransHop(new TransHopMeta( beamInputStepMeta, dummyStepMeta ) );


    // Add the output step
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "customers" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( dummyStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }

  public static final TransMeta generateBeamGroupByTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the io step
    //
    BeamInputMeta beamInputMeta = new BeamInputMeta();
    beamInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    beamInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta beamInputStepMeta = new StepMeta(inputStepname, beamInputMeta);
    beamInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    transMeta.addStep( beamInputStepMeta );


    // Add a dummy in between to get started...
    //
    MemoryGroupByMeta memoryGroupByMeta = new MemoryGroupByMeta();
    memoryGroupByMeta.allocate(1, 2 );
    memoryGroupByMeta.getGroupField()[0] = "state";
    // count(id)
    memoryGroupByMeta.getAggregateField()[0] = "nrIds";
    memoryGroupByMeta.getSubjectField()[0] = "id";
    memoryGroupByMeta.getAggregateType()[0] = MemoryGroupByMeta.TYPE_GROUP_COUNT_ALL;
    // sum(id)
    memoryGroupByMeta.getAggregateField()[1] = "sumIds";
    memoryGroupByMeta.getSubjectField()[1] = "id";
    memoryGroupByMeta.getAggregateType()[1] = MemoryGroupByMeta.TYPE_GROUP_SUM;

    StepMeta memoryGroupByStepMeta = new StepMeta("Group By", memoryGroupByMeta);
    transMeta.addStep( memoryGroupByStepMeta );
    transMeta.addTransHop(new TransHopMeta( beamInputStepMeta, memoryGroupByStepMeta ) );

    // Add the output step
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "grouped" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( memoryGroupByStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }


  public static final TransMeta generateFilterRowsTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the io step
    //
    BeamInputMeta beamInputMeta = new BeamInputMeta();
    beamInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    beamInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta beamInputStepMeta = new StepMeta(inputStepname, beamInputMeta);
    beamInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    transMeta.addStep( beamInputStepMeta );


    // Add 2 add constants steps A and B
    //
    ConstantMeta constantA = new ConstantMeta();
    constantA.allocate( 1 );
    constantA.getFieldName()[0]="label";
    constantA.getFieldType()[0]="String";
    constantA.getValue()[0]="< 'k'";
    StepMeta constantAMeta = new StepMeta("A", constantA);
    transMeta.addStep(constantAMeta);

    ConstantMeta constantB = new ConstantMeta();
    constantB.allocate( 1 );
    constantB.getFieldName()[0]="label";
    constantB.getFieldType()[0]="String";
    constantB.getValue()[0]=">= 'k'";
    StepMeta constantBMeta = new StepMeta("B", constantB);
    transMeta.addStep(constantBMeta);


    // Add Filter rows step looking for customers name > "k"
    // Send rows to A (true) and B (false)
    //
    FilterRowsMeta filter = new FilterRowsMeta();
    filter.getCondition().setLeftValuename( "name" );
    filter.getCondition().setFunction( Condition.FUNC_SMALLER );
    filter.getCondition().setRightExact( new ValueMetaAndData( "value", "k" ) );
    filter.setTrueStepname( "A" );
    filter.setFalseStepname( "B" );
    StepMeta filterMeta = new StepMeta("Filter", filter);
    transMeta.addStep( filterMeta );
    transMeta.addTransHop( new TransHopMeta( beamInputStepMeta, filterMeta ) );
    transMeta.addTransHop( new TransHopMeta( filterMeta, constantAMeta ) );
    transMeta.addTransHop( new TransHopMeta( filterMeta, constantBMeta ) );

    // Add a dummy behind it all to flatten/merge the data again...
    //
    DummyTransMeta dummyTransMeta = new DummyTransMeta();
    StepMeta dummyStepMeta = new StepMeta("Flatten", dummyTransMeta);
    transMeta.addStep( dummyStepMeta );
    transMeta.addTransHop(new TransHopMeta( constantAMeta, dummyStepMeta ) );
    transMeta.addTransHop(new TransHopMeta( constantBMeta, dummyStepMeta ) );

    // Add the output step
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "filter-test" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( dummyStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }

  public static final TransMeta generateSwitchCaseTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the io step
    //
    BeamInputMeta beamInputMeta = new BeamInputMeta();
    beamInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    beamInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta beamInputStepMeta = new StepMeta(inputStepname, beamInputMeta);
    beamInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    beamInputStepMeta.setDraw( true );
    transMeta.addStep( beamInputStepMeta );



    // Add 4 add constants steps CA and FL, NY, Default
    //
    String[] stateCodes = new String[] { "CA", "FL", "NY", "AR", "Default" };
    for (String stateCode : stateCodes) {
      ConstantMeta constant = new ConstantMeta();
      constant.allocate( 1 );
      constant.getFieldName()[0]="Comment";
      constant.getFieldType()[0]="String";
      constant.getValue()[0]=stateCode+" : some comment";
      StepMeta constantMeta = new StepMeta(stateCode, constant);
      constantMeta.setDraw( true );
      transMeta.addStep(constantMeta);
    }

    // Add Switch / Case step looking switching on stateCode field
    // Send rows to A (true) and B (false)
    //
    SwitchCaseMeta switchCaseMeta = new SwitchCaseMeta();
    switchCaseMeta.allocate();
    switchCaseMeta.setFieldname( "stateCode" );
    switchCaseMeta.setCaseValueType( ValueMetaInterface.TYPE_STRING );
    // Last one in the array is the Default target
    //
    for (int i=0;i<stateCodes.length-1;i++) {
      String stateCode = stateCodes[i];
      List<SwitchCaseTarget> caseTargets = switchCaseMeta.getCaseTargets();
      SwitchCaseTarget target = new SwitchCaseTarget();
      target.caseValue = stateCode;
      target.caseTargetStepname = stateCode;
      caseTargets.add(target);
    }
    switchCaseMeta.setDefaultTargetStepname( stateCodes[stateCodes.length-1]  );
    switchCaseMeta.searchInfoAndTargetSteps( transMeta.getSteps() );
    StepMeta switchCaseStepMeta = new StepMeta("Switch/Case", switchCaseMeta);
    switchCaseStepMeta.setDraw( true );
    transMeta.addStep( switchCaseStepMeta );
    transMeta.addTransHop( new TransHopMeta( beamInputStepMeta, switchCaseStepMeta ) );

    for (String stateCode : stateCodes) {
      transMeta.addTransHop( new TransHopMeta( switchCaseStepMeta, transMeta.findStep( stateCode ) ) );
    }

    // Add a dummy behind it all to flatten/merge the data again...
    //
    DummyTransMeta dummyTransMeta = new DummyTransMeta();
    StepMeta dummyStepMeta = new StepMeta("Flatten", dummyTransMeta);
    dummyStepMeta.setDraw( true );
    transMeta.addStep( dummyStepMeta );

    for (String stateCode : stateCodes) {
      transMeta.addTransHop( new TransHopMeta( transMeta.findStep( stateCode ), dummyStepMeta ) );
    }

    // Add the output step
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "switch-case-test" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    beamOutputStepMeta.setDraw( true );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( dummyStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }


  public static final TransMeta generateStreamLookupTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the main io step
    //
    BeamInputMeta beamInputMeta = new BeamInputMeta();
    beamInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    beamInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta beamInputStepMeta = new StepMeta(inputStepname, beamInputMeta);
    beamInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    transMeta.addStep( beamInputStepMeta );

    StepMeta lookupBeamInputStepMeta = beamInputStepMeta;

    // Add a Memory Group By step which will
    MemoryGroupByMeta memoryGroupByMeta = new MemoryGroupByMeta();
    memoryGroupByMeta.allocate( 1, 1 );
    memoryGroupByMeta.getGroupField()[0] = "stateCode";
    memoryGroupByMeta.getAggregateType()[0] = MemoryGroupByMeta.TYPE_GROUP_COUNT_ALL;
    memoryGroupByMeta.getAggregateField()[0] = "rowsPerState";
    memoryGroupByMeta.getSubjectField()[0] = "id";
    StepMeta memoryGroupByStepMeta = new StepMeta("rowsPerState", memoryGroupByMeta);
    transMeta.addStep( memoryGroupByStepMeta );
    transMeta.addTransHop( new TransHopMeta( lookupBeamInputStepMeta, memoryGroupByStepMeta ) );

    // Add a Stream Lookup step ...
    //
    StreamLookupMeta streamLookupMeta = new StreamLookupMeta();
    streamLookupMeta.allocate( 1, 1 );
    streamLookupMeta.getKeystream()[0] = "stateCode";
    streamLookupMeta.getKeylookup()[0] = "stateCode";
    streamLookupMeta.getValue()[0] = "rowsPerState";
    streamLookupMeta.getValueName()[0] = "nrPerState";
    streamLookupMeta.getValueDefault()[0] = null;
    streamLookupMeta.getValueDefaultType()[0] = ValueMetaInterface.TYPE_INTEGER;
    streamLookupMeta.setMemoryPreservationActive( false );
    streamLookupMeta.getStepIOMeta().getInfoStreams().get(0).setStepMeta( memoryGroupByStepMeta ); // Read from Mem.GroupBy
    StepMeta streamLookupStepMeta = new StepMeta("Stream Lookup", streamLookupMeta);
    transMeta.addStep(streamLookupStepMeta);
    transMeta.addTransHop( new TransHopMeta( beamInputStepMeta, streamLookupStepMeta ) ); // Main io
    transMeta.addTransHop( new TransHopMeta( memoryGroupByStepMeta, streamLookupStepMeta ) ); // info stream

    // Add the output step to write results
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "stream-lookup" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( streamLookupStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }

  public static final TransMeta generateMergeJoinTransMeta( String transname, String inputStepname, String outputStepname, IMetaStore metaStore ) throws Exception {

    MetaStoreFactory<FileDefinition> factory = new MetaStoreFactory<>( FileDefinition.class, metaStore, PentahoDefaults.NAMESPACE );
    FileDefinition customerFileDefinition = createCustomersInputFileDefinition();
    factory.saveElement( customerFileDefinition );
    FileDefinition statePopulationFileDefinition = createStatePopulationInputFileDefinition();
    factory.saveElement( statePopulationFileDefinition );

    TransMeta transMeta = new TransMeta(  );
    transMeta.setName( transname );
    transMeta.setMetaStore( metaStore );

    // Add the left io step
    //
    BeamInputMeta leftInputMeta = new BeamInputMeta();
    leftInputMeta.setInputLocation( "/tmp/customers/io/customers-100.txt" );
    leftInputMeta.setFileDescriptionName( customerFileDefinition.getName() );
    StepMeta leftInputStepMeta = new StepMeta(inputStepname+" Left", leftInputMeta);
    leftInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    transMeta.addStep( leftInputStepMeta );

    BeamInputMeta rightInputMeta = new BeamInputMeta();
    rightInputMeta.setInputLocation( "/tmp/customers/io/state-data.txt" );
    rightInputMeta.setFileDescriptionName( statePopulationFileDefinition.getName() );
    StepMeta rightInputStepMeta = new StepMeta(inputStepname+" Right", rightInputMeta);
    rightInputStepMeta.setStepID( BeamConst.STRING_BEAM_INPUT_PLUGIN_ID );
    transMeta.addStep( rightInputStepMeta );


    // Add a Merge Join step
    //
    MergeJoinMeta mergeJoin = new MergeJoinMeta();
    mergeJoin.allocate( 1, 1 );
    mergeJoin.getKeyFields1()[0] = "state";
    mergeJoin.getKeyFields2()[0] = "state";
    mergeJoin.setJoinType(MergeJoinMeta.join_types[3] ); // FULL OUTER
    mergeJoin.getStepIOMeta().getInfoStreams().get(0).setStepMeta( leftInputStepMeta );
    mergeJoin.getStepIOMeta().getInfoStreams().get(1).setStepMeta( rightInputStepMeta );
    StepMeta mergeJoinStepMeta = new StepMeta("Merge Join", mergeJoin);
    transMeta.addStep( mergeJoinStepMeta );
    transMeta.addTransHop( new TransHopMeta( leftInputStepMeta, mergeJoinStepMeta ) );
    transMeta.addTransHop( new TransHopMeta( rightInputStepMeta, mergeJoinStepMeta ) );

    // Add the output step to write results
    //
    BeamOutputMeta beamOutputMeta = new BeamOutputMeta();
    beamOutputMeta.setOutputLocation( "/tmp/customers/output/" );
    beamOutputMeta.setFileDescriptionName( null );
    beamOutputMeta.setFilePrefix( "merge-join" );
    beamOutputMeta.setFileSuffix( ".csv" );
    beamOutputMeta.setWindowed( false ); // Not yet supported
    StepMeta beamOutputStepMeta = new StepMeta(outputStepname, beamOutputMeta);
    beamOutputStepMeta.setStepID( "BeamOutput" );
    transMeta.addStep( beamOutputStepMeta );
    transMeta.addTransHop(new TransHopMeta( mergeJoinStepMeta, beamOutputStepMeta ) );

    return transMeta;
  }




  public static FileDefinition createCustomersInputFileDefinition() {
    FileDefinition fileDefinition = new FileDefinition();
    fileDefinition.setName( "Customers" );
    fileDefinition.setDescription( "File description of customers.txt" );

    // id;name;firstname;zip;city;birthdate;street;housenr;stateCode;state

    fileDefinition.setSeparator( ";" );
    fileDefinition.setEnclosure( null ); // NOT SUPPORTED YET

    List<FieldDefinition> fields = fileDefinition.getFieldDefinitions();
    fields.add( new FieldDefinition( "id", "Integer", 9, 0, " #" ) );
    fields.add( new FieldDefinition( "name", "String", 50, 0 ) );
    fields.add( new FieldDefinition( "firstname", "String", 50, 0 ) );
    fields.add( new FieldDefinition( "zip", "String", 20, 0 ) );
    fields.add( new FieldDefinition( "city", "String", 100, 0 ) );
    fields.add( new FieldDefinition( "birthdate", "Date", -1, -1, "yyyy/MM/dd" ) );
    fields.add( new FieldDefinition( "street", "String", 100, 0 ) );
    fields.add( new FieldDefinition( "housenr", "String", 20, 0 ) );
    fields.add( new FieldDefinition( "stateCode", "String", 10, 0 ) );
    fields.add( new FieldDefinition( "state", "String", 100, 0 ) );

    return fileDefinition;
  }

  public static FileDefinition createStatePopulationInputFileDefinition() {
    FileDefinition fileDefinition = new FileDefinition();
    fileDefinition.setName( "StatePopulation" );
    fileDefinition.setDescription( "File description of state-data.txt" );

    // state;population

    fileDefinition.setSeparator( ";" );
    fileDefinition.setEnclosure( null ); // NOT SUPPORTED YET

    List<FieldDefinition> fields = fileDefinition.getFieldDefinitions();
    fields.add( new FieldDefinition( "state", "String", 100, 0 ) );
    fields.add( new FieldDefinition( "population", "Integer", 9, 0, "#" ) );

    return fileDefinition;
  }

}