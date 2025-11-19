package com.macstadium.orka;

public class CommonConstants {
  public static final String IMAGE_ID_PARAM_NAME = "cloud.orka.image.id";
  public static final String INSTANCE_ID_PARAM_NAME = "cloud.orka.instance.id";
  public static final String STARTING_INSTANCE_ID_PARAM_NAME = "cloud.orka.startingInstanceId";
  public static final String METADATA_FILE_PREFIX = "orka_metadata_file";
  public static final String STARTING_INSTANCE_ID_CONFIG_PARAM = "teamcity.agent.startingInstanceId";
  public static final String VM_METADATA_VALIDATION_PATTERN = "^([a-zA-Z0-9_-]+=[^,=]+)(,[a-zA-Z0-9_-]+=[^,=]+)*$";
}
