package com.comcast.oscar.configurationfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.snmp4j.asn1.BER;

import com.comcast.oscar.ber.BERService;
import com.comcast.oscar.compiler.DocsisConstants;
import com.comcast.oscar.compiler.PacketCableCompiler;
import com.comcast.oscar.compiler.PacketCableConstants;
import com.comcast.oscar.constants.Constants;
import com.comcast.oscar.datatype.DataTypeDictionaryReference;
import com.comcast.oscar.datatype.DataTypeFormatConversion;
import com.comcast.oscar.dictionary.Dictionary;
import com.comcast.oscar.dictionary.DictionarySQLConstants;
import com.comcast.oscar.dictionary.DictionarySQLQueries;
import com.comcast.oscar.netsnmp.NetSNMP;
import com.comcast.oscar.tlv.TlvBuilder;
import com.comcast.oscar.tlv.TlvDisassemble;
import com.comcast.oscar.tlv.TlvException;
import com.comcast.oscar.tlv.TlvVariableBinding;
import com.comcast.oscar.utilities.BinaryConversion;
import com.comcast.oscar.utilities.HexString;
import com.comcast.oscar.utilities.JSONTools;
import com.comcast.oscar.utilities.PrettyPrint;

/**
 * @bannerLicense
	Copyright 2015 Comcast Cable Communications Management, LLC<br>
	___________________________________________________________________<br>
	Licensed under the Apache License, Version 2.0 (the "License")<br>
	you may not use this file except in compliance with the License.<br>
	You may obtain a copy of the License at<br>
	http://www.apache.org/licenses/LICENSE-2.0<br>
	Unless required by applicable law or agreed to in writing, software<br>
	distributed under the License is distributed on an "AS IS" BASIS,<br>
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br>
	See the License for the specific language governing permissions and<br>
	limitations under the License.<br>


 * @author Maurice Garcia (maurice.garcia.2015@gmail.com)
 */


public class ConfigurationFileExport {
	
	/*House the Configuration Byte Array */
	private byte[] bTLV;

	private final boolean debug = Boolean.FALSE;	
	
	private ArrayList<JSONObject> aljoTopLevelTlvDictionary;	
	private final Integer RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH = -1;	
	private Map<Integer,String> BER_DATA_TYPE = new HashMap<Integer,String>();	
	private DictionarySQLQueries dsqDictionarySQLQueries = null;	
	private String sConfigurationFileStart;	
	private int iConfigurationFileType = -1;	
	private boolean boolVerboseExport = true;
	private boolean boolDottextOutputFormat = true;
	private boolean boolTlvCommentSuppress = false;
	
	public final String END_OF_CODE_BLOCK = "\\*EOCB*\\";
	
	public static final Integer DOCSIS_PKTCBL 	= -1;
	public static final Integer DOCSIS_VER_10 	= ConfigurationFileTypeConstants.DOCSIS_10_CONFIGURATION_TYPE;
	public static final Integer DOCSIS_VER_11 	= ConfigurationFileTypeConstants.DOCSIS_11_CONFIGURATION_TYPE;
	public static final Integer DOCSIS_VER_20 	= ConfigurationFileTypeConstants.DOCSIS_20_CONFIGURATION_TYPE;
	public static final Integer DOCSIS_VER_30 	= ConfigurationFileTypeConstants.DOCSIS_30_CONFIGURATION_TYPE;
	public static final Integer DOCSIS_VER_31 	= ConfigurationFileTypeConstants.DOCSIS_31_CONFIGURATION_TYPE;
	
	public static final Integer PKT_CBL_VER_10 	= ConfigurationFileTypeConstants.PKT_CABLE_10_CONFIGURATION_TYPE;
	public static final Integer PKT_CBL_VER_15 	= ConfigurationFileTypeConstants.PKT_CABLE_15_CONFIGURATION_TYPE;
	public static final Integer PKT_CBL_VER_20 	= ConfigurationFileTypeConstants.PKT_CABLE_20_CONFIGURATION_TYPE;
	
	public static final Integer DPOE_VER_10 	= ConfigurationFileTypeConstants.DPOE_10_CONFIGURATION_TYPE;
	public static final Integer DPOE_VER_20 	= ConfigurationFileTypeConstants.DPOE_20_CONFIGURATION_TYPE;
	
	public static final Boolean EXPORT_DEFAULT_TLV = true;
	public static final Boolean EXPORT_FOUND_TLV = false;
	
	public static final Boolean TEXTUAL_OID_FORMAT = true;
	public static final Boolean DOTTED_OID_FORMAT = false;
	
	public static final Boolean SUPPRESS_TLV_COMMENT = true;
	
	/**
	 * @deprecated - This is no longer supported but will work Only support DOCSIS and PacketCable 
	 * @param fTLV - Configuration File - Only support DOCSIS and PacketCable
	 */
	public ConfigurationFileExport (File fTLV) {
	
		//Convert to Byte Array
		this.bTLV = HexString.fileToByteArray(fTLV);
		
		if (debug) 
			System.out.println("ConfigrationFileExport() -> FileByteLength: " +   this.bTLV.length);

		if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
						
		} else {
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
		}
		
		init();
		
		//Build Dictionary
		tlvToDictionary ();
		
	}

	/**
	 * 
	 * @param fTLV - Will Support all Configuration file Types: DOCSIS, PacketCable and DPoE
	 * @param iConfigurationFileType - Set Configuration Type via Static FIELDS*/
	public ConfigurationFileExport (File fTLV, int iConfigurationFileType) {

		boolean localDebug = Boolean.FALSE;
		
		this.bTLV = HexString.fileToByteArray(fTLV);
		
		//DumpTLV to STDOUT
		if (localDebug) {
			System.out.println(TlvBuilder.tlvDump(this.bTLV));
		}
		
		this.iConfigurationFileType = iConfigurationFileType;
		
		/* This is to support the deprecated method public ConfigurationFileExport (File fTLV) */
		if ((iConfigurationFileType <= DOCSIS_PKTCBL)) {
					
			//Convert to Byte Array
			this.bTLV = HexString.fileToByteArray(fTLV);
			
			if (debug) 
				System.out.println("ConfigrationFileExport(f,i) -> FileByteLength: " +   this.bTLV.length);

			if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
				
				if (localDebug) System.out.println("Packet Cable Configuration File - Anonomous - ConfigType -> (" + iConfigurationFileType + ")");
				
				dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
							
			} else {
				
				if (localDebug) System.out.println("DOCSIS Configuration File - Anonomous - ConfigType -> (" + iConfigurationFileType + ")");
				
				dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
			}
			
			init();
			
		} else if ((iConfigurationFileType >= DOCSIS_VER_10) && (iConfigurationFileType <= DOCSIS_VER_31)) {
			
			if (localDebug) System.out.println("DOCSIS Configuration File - ConfigType -> (" + iConfigurationFileType + ")");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
			
			init();
			
		} else if ((iConfigurationFileType >= PKT_CBL_VER_10) && (iConfigurationFileType <= PKT_CBL_VER_20)) {
			
			if (localDebug) System.out.println("PacketCable Configuration File - ConfigType -> (" + iConfigurationFileType + ")");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
			
			init();
			
		} else if ((iConfigurationFileType >= DPOE_VER_20) && (iConfigurationFileType <= DPOE_VER_20)) {
			
			if (localDebug) System.out.println("DPoE Configuration File - ConfigType -> (" + iConfigurationFileType + ")");

			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DPOE_DICTIONARY_TABLE_NAME);
			
			init(iConfigurationFileType);
		}
		
		//Build Dictionary
		tlvToDictionary ();
		
	}
	
	/**
	 * This constructor checks the first Byte to determine if it is a Packet Cable File or DOCSIS File
	 * Byte = 0xFE = Packet Cable
	 * @deprecated - This is no longer supported but will work Only support DOCSIS and PacketCable 
	 * @param tbTLV - Will ONLY Support Configuration file Types: DOCSIS and PacketCable
	 */
	public ConfigurationFileExport (TlvBuilder tbTLV) {
		
		boolean localDebug = Boolean.FALSE;
		
		//Convert to Byte Array
		this.bTLV = tbTLV.toByteArray();
		
		if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
			
			if (localDebug|debug) {
				System.out.println("ConfigrationFileExport(tb) - PacketCable File");
			}
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
						
		} else {
			
			if (localDebug|debug) {
				System.out.println("ConfigrationFileExport(tb) - DOCSIS File");
			}
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
						
		}
		
		init();
		
		//Build Dictionary
		tlvToDictionary ();
			
	}
	
	/**
	 * 
	 * @param tbTLV
	 * @param iConfigurationFileType
	 */
	public ConfigurationFileExport (TlvBuilder tbTLV, int iConfigurationFileType) {
		
		boolean localDebug = Boolean.FALSE;
		
		//Convert to Byte Array
		this.bTLV = tbTLV.toByteArray();
		
		this.iConfigurationFileType = iConfigurationFileType;
		
		if ((iConfigurationFileType >= DOCSIS_VER_10) && (iConfigurationFileType <= DOCSIS_VER_31)) {
			
			if (localDebug) System.out.println("DOCSIS Configuration File");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
			
			init();
			
		} else if ((iConfigurationFileType >= PKT_CBL_VER_10) && (iConfigurationFileType <= PKT_CBL_VER_20)) {
			
			if (localDebug) System.out.println("PacketCable Configuration File");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
			
			init();
			
		} else if ((iConfigurationFileType >= DPOE_VER_20) && (iConfigurationFileType <= DPOE_VER_20)) {
			
			if (localDebug) System.out.println("DPoE Configuration File");

			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DPOE_DICTIONARY_TABLE_NAME);
			
			init(iConfigurationFileType);
		}
				
		//Build Dictionary
		tlvToDictionary ();
			
	}	
	
	/**
	 * @deprecated - This is no longer supported but will work Only support DOCSIS and PacketCable 
	 * @param tbTLV
	 * @param boolStripFinalize boolean
	 */
	public ConfigurationFileExport (TlvBuilder tbTLV, boolean boolStripFinalize) {
			
		boolean localDebug = Boolean.FALSE;
		
		if (boolStripFinalize) {
			
			if (debug|localDebug)
				System.out.println("ConfigrationFileExport(tb,bool) - StripFinalize");
			
			//Check for PacketCable Marker
			if (tbTLV.toByteArray()[0] == PacketCableConstants.FILE_MARKER) {
				
				if (debug|localDebug)
					System.out.println("ConfigrationFileExport(tb,bool) - StripFinalize - PacketCable File");
				
				try {
					this.bTLV = ConfigurationFile.stripFinalize(tbTLV, ConfigurationFile.PKT_CBL_VER_20).toByteArray();
				} catch (TlvException e) {
					e.printStackTrace();
				}
				
			} else {
				
				if (debug|localDebug) {
					System.out.println("ConfigrationFileExport(tb,bool) - StripFinalize - DOCSIS File");}
				
				try {
					this.bTLV = ConfigurationFile.stripFinalize(tbTLV, ConfigurationFile.DOCSIS_VER_31).toByteArray();
				} catch (TlvException e) {
					e.printStackTrace();
				}
			}
			
		} else {
			//Convert to Byte Array
			this.bTLV = tbTLV.toByteArray();
		}
					
		if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
						
		} else {
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
			
			
		}
		
		init();
		
		//Build Dictionary
		tlvToDictionary ();
			
	}
	
	/**
	 * @param cfConfigurationFile - Will Support all Configuration file Types: DOCSIS, PacketCable and DPoE
	 */
	public ConfigurationFileExport (ConfigurationFile cfConfigurationFile) {
		
		boolean localDebug = Boolean.FALSE;
		
		//Convert to Byte Array
		this.bTLV = cfConfigurationFile.toByteArray();
		
		//DumpTLV to STDOUT
		if (localDebug) {	
			System.out.println(TlvBuilder.tlvDump(this.bTLV));		
		}
		
		//Update to determine what type of configuration is selected
		this.iConfigurationFileType = cfConfigurationFile.getConfigurationFileType();
		
		//Check for DPoE Type Configuration File
		if ((this.iConfigurationFileType >= DPOE_VER_20) && (this.iConfigurationFileType <= DPOE_VER_20)) {
			
			if (localDebug) System.out.println("ConfigurationFileExport(cf) -> DPoE Configuration File - ConfigType -> (" + iConfigurationFileType + ")");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DPOE_DICTIONARY_TABLE_NAME);
		
			init(this.iConfigurationFileType);				
		
		} else {
			
			if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
				
				if (localDebug) System.out.println("ConfigurationFileExport(cf) -> PacketCable Configuration File - ConfigType -> (" + iConfigurationFileType + ")");
				
				dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
				
				init();	
				
			} else {
				
				if (localDebug) System.out.println("ConfigurationFileExport(cf) -> DOCSIS Configuration File - ConfigType -> (" + iConfigurationFileType + ")");
				
				dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
				
				init();
			}
			
		}
			
		//Build Dictionary
		tlvToDictionary ();
		
	}
	
	/**
	 * This will only produce the Full Default Configuration Options 
	 * @param iConfigurationFileType - Will Support all Configuration file Types: DOCSIS, PacketCable and DPoE - Set Configuration Type via Static FIELD
	 */
	public ConfigurationFileExport (int iConfigurationFileType) {
		
		Boolean localDebug = Boolean.FALSE;
		
		JSONArray jaTlvDictionary = null;
		
		this.iConfigurationFileType = iConfigurationFileType;
		
		if (localDebug)
			System.out.println("ConfigurationFileExport(i): ConfigurationFileType: " + iConfigurationFileType);
		
		if ((this.iConfigurationFileType >= DOCSIS_VER_10) && (this.iConfigurationFileType <= DOCSIS_VER_31)) {
			
			if (localDebug)
				System.out.println("ConfigurationFileExport(i): DOCSIS -> CONFIGURATION-TYPE");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DOCSIS_DICTIONARY_TABLE_NAME);
			
			jaTlvDictionary = dsqDictionarySQLQueries.getAllTlvDefinition(DictionarySQLQueries.CONFIGURATION_FILE_TYPE_DOCSIS);
			
			bTLV = docsisPsuedoTLVArray();
			
			init();
			
		} else if ((this.iConfigurationFileType >= PKT_CBL_VER_10) && (this.iConfigurationFileType <= PKT_CBL_VER_20)) {

			if (localDebug)
				System.out.println("ConfigurationFileExport(i): PACKET-CABLE -> CONFIGURATION-TYPE");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.PACKET_CABLE_DICTIONARY_TABLE_NAME);
			
			jaTlvDictionary = dsqDictionarySQLQueries.getAllTlvDefinition(DictionarySQLQueries.CONFIGURATION_FILE_TYPE_PACKET_CABLE);
			
			bTLV = packetCablePsuedoTLVArray();
			
			init();
			
		} else if ((this.iConfigurationFileType >= DPOE_VER_10) && (this.iConfigurationFileType <= DPOE_VER_20)) {

			if (localDebug)
				System.out.println("ConfigurationFileExport(i): DPoE -> CONFIGURATION-TYPE");
			
			dsqDictionarySQLQueries = new DictionarySQLQueries(DictionarySQLQueries.DPOE_DICTIONARY_TABLE_NAME);

			jaTlvDictionary = dsqDictionarySQLQueries.getAllTlvDefinition(DictionarySQLQueries.CONFIGURATION_FILE_TYPE_DPOE);

			bTLV = packetCablePsuedoTLVArray();
			
			init(iConfigurationFileType);
		}
		
		convertJSONArrayDictToJSONObjectArrayList(jaTlvDictionary);
	}
	
	/**
	 * @deprecated
	 * @since v1.0.1
	 * @param iIndentation
	 * @return String*/
	public String toPrettyPrint (int iIndentation) {
		
		boolean localDebug = Boolean.FALSE;
		
		StringBuilder sbTlvPrettyPrint = new StringBuilder(banner());
		
		if (debug|localDebug) 
			System.out.println("ConfigrationFileExport().toPrettyPrint() -> aljoTopLevelTlvDictionaryLength: " +   aljoTopLevelTlvDictionary.size());
		
		sbTlvPrettyPrint.append( sConfigurationFileStart + " {\n");
		
		for (JSONObject joTLV : aljoTopLevelTlvDictionary) {
			
			//Added for Default Configuration file option
			if (joTLV.length() == 0) {continue;}
			
			try {
							
				if (joTLV.getBoolean(Dictionary.ARE_SUBTYPES)) {
				
					sbTlvPrettyPrint
						.append("\n\t")
						.append(joTLV.get(Dictionary.TLV_NAME))
						.append(" {\n");
					
					sbTlvPrettyPrint.append(topLevelTLVCodeBlock(joTLV.getJSONArray(Dictionary.SUBTYPE_ARRAY),2));
					
				} else {					
					sbTlvPrettyPrint.append(topLevelTLVCodeBlock(joTLV,2));
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
		}
		
		sbTlvPrettyPrint.append("}\n\n");
		
		return sbTlvPrettyPrint.toString();
	}
	
	/**
	 * 
	 * @param boolIncludeDefaultTLV = True == will include default TLV if no value is found 
	 * @return String of the Compiled Configuration File*/
	public String toPrettyPrint(boolean boolIncludeDefaultTLV) {
		
		//Deprecated setExportVerbose()
		this.boolVerboseExport = boolIncludeDefaultTLV;
	
		//Set ConfigurationFile Type
		 String sConfigurationFile = "";
		
		for (JSONObject joTLV : aljoTopLevelTlvDictionary) {
			
			//Added for Default Configuration file option
			if (joTLV.length() == 0) {continue;}
			
			try {
							
				if (joTLV.getBoolean(Dictionary.ARE_SUBTYPES)) {
				
					sConfigurationFile += ("\n\t") + (joTLV.get(Dictionary.TLV_NAME)) + (" {\n");
					sConfigurationFile += (topLevelTLVCodeBlock(joTLV.getJSONArray(Dictionary.SUBTYPE_ARRAY),2));
					
				} else {
					sConfigurationFile += (topLevelTLVCodeBlock(joTLV,2));
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
		}
			
		com.comcast.oscar.utilities.PrettyPrint ppConfigurationFile = 
				new com.comcast.oscar.utilities.PrettyPrint((sConfigurationFileStart + " {\n") + (sConfigurationFile) + ("}\n"));
		
		String sConfigurationOuput = "";
		
		if (boolTlvCommentSuppress) {
			sConfigurationOuput = ppConfigurationFile.toString().replaceAll("/\\*.*?\\*/","");
		} else {
			sConfigurationOuput = ppConfigurationFile.toString();
		}
		
		return (banner().toString()) + sConfigurationOuput;
	}
	
	/**
	 * 
	 * @param boolIncludeDefaultTLV True == will include default TLV if no value is found 
	 * @param boolTlvCommentSuppress True == will NOT include TLV Comment 
	 * @return
	 */
	public String toPrettyPrint(boolean boolIncludeDefaultTLV,boolean boolTlvCommentSuppress) {
		this.boolTlvCommentSuppress = boolTlvCommentSuppress;
		return toPrettyPrint(boolIncludeDefaultTLV);
	}
	
	/**
	 *
	 * @return Map<Integer,String>*/
	public Map<Integer,String> toHex () {
		return null;
	}
	
	/**
	 * 
	 * @return TlvBuilder
	 * @throws TlvException */
	public TlvBuilder getTlvBuilder() throws TlvException {
		
		TlvBuilder tb = new TlvBuilder();
		
		tb.add(new HexString(this.bTLV));
		
		return tb;
	}

	/**
	 * 
	 * Example: TLV Dot Notation: 25.1.2
	 * 
	 * @param sTlvDotNotation
	 * @return String*/
	public String getTlvDefintion (String sTlvDotNotation) {
		
		boolean localDebug = Boolean.FALSE;
		
		String sTlvDescription = "";
		String sTlvName = "";
		String sDisplayHint = "";
		
		List<String> lsTlvDotNotation = new ArrayList<String>();
		
		lsTlvDotNotation = Arrays.asList(sTlvDotNotation.split("\\."));
		
		if (debug|localDebug)
			System.out.println("ConfigrationFileExport.getTlvDefintion(): " + lsTlvDotNotation.toString());
		
		//Get TLV Dictionary for the Top Level
		JSONObject joTlvDictionary = dsqDictionarySQLQueries.getTlvDefinition(Integer.decode(lsTlvDotNotation.get(0)));
		
		//Search for TLV Definition
		if (lsTlvDotNotation.size() == 1) {
			
			try {
				sTlvName = joTlvDictionary.getString(Dictionary.TLV_NAME);
				sTlvDescription = joTlvDictionary.getString(Dictionary.TLV_DESCRIPTION);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			sDisplayHint = getDisplayHint(joTlvDictionary);
		
		} else if (lsTlvDotNotation.size() >= 1) {
			
			int iRecursiveSearch = 0;
			
			while (iRecursiveSearch < lsTlvDotNotation.size()) {
				
				if (debug|localDebug)
					System.out.println("ConfigrationFileExport.getTlvDefintion(): WHILE-LOOP");
			
				try {
					
					if (joTlvDictionary.getString(Dictionary.TYPE).equals(lsTlvDotNotation.get(iRecursiveSearch))) {
						
						if (joTlvDictionary.getBoolean(Dictionary.ARE_SUBTYPES)) {
							
							try {
								JSONArray jaTlvDictionary = joTlvDictionary.getJSONArray(Dictionary.SUBTYPE_ARRAY);
								
								for (int iIndex = 0 ; iIndex < jaTlvDictionary.length() ; iIndex++) {
									
									if (debug|localDebug)
										System.out.println("ConfigrationFileExport.getTlvDefintion(): FOR-LOOP");
									
									JSONObject joTlvDictionaryTemp = jaTlvDictionary.getJSONObject(iIndex);
									
									if (joTlvDictionaryTemp.getString(Dictionary.TYPE).equals(lsTlvDotNotation.get(iRecursiveSearch+1))) {
										joTlvDictionary = joTlvDictionaryTemp;
										iRecursiveSearch++;
										break;
									}
								}
								
							} catch (JSONException e) {
								e.printStackTrace();
							}
							
						} else {
							sTlvName = joTlvDictionary.getString(Dictionary.TLV_NAME);
							
							sTlvDescription = joTlvDictionary.getString(Dictionary.TLV_DESCRIPTION);
							
							sDisplayHint = getDisplayHint(joTlvDictionary);
							
							iRecursiveSearch++;
						}
					}
				
				} catch (JSONException e1) {
					e1.printStackTrace();
				}	
			}			
		}
			
		return "\n\n" + sTlvName + ":\n\n" + PrettyPrint.ToParagraphForm(sTlvDescription)  + "\n\n" + "String Format:\n" + sDisplayHint;		
	}
	
	/**
	 * 
	 * @param iTlvType
	
	 * @return JSONArray
	 */
	public JSONArray getTopLevelTLVJSON(int iTlvType) {
		
		JSONArray jaTopLevelTLV = new JSONArray();
		
		for (JSONObject joTopLevelTLV : aljoTopLevelTlvDictionary) {
			
			try {
				
				if (joTopLevelTLV.getInt(Dictionary.TYPE) == iTlvType) {
					jaTopLevelTLV.put(joTopLevelTLV);	
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}		
		return jaTopLevelTLV;
	}
	
	/**
	 * Method will default to no Verbose
	 * @param fOutput
	 * @return true is write, false is it did not write */
	public boolean writeToDisk(File fOutput) {
		return writeToDisk(fOutput,ConfigurationFileExport.EXPORT_FOUND_TLV);
	}
	
	/**
	 * 
	 * @param fOutput
	 * @param boolVerbose
	 * @return true is write, false is it did not write */
	public boolean writeToDisk(File fOutput,boolean boolVerbose) {

		boolean localDebug = Boolean.FALSE;
		
		byte[] bConfiguration = HexString.toByteArray(HexString.asciiToHex(toPrettyPrint(boolVerbose)));
		
		if (bConfiguration == null) {

			if (debug|localDebug) {
				System.out.println("ConfigurationFile.writeToDisk() - NULL ByteArray");
			}
			
			return false;
		}
		
		if (debug|localDebug) {
			System.out.println("ConfigurationFile.writeToDisk() " +
									" - Total Byte Count: " + bConfiguration.length +
									" - FileName: " + fOutput.getName());
		}
		
		OutputStream out = null;
		
		if (fOutput.getName() != null) {
			
			try {
				out = new FileOutputStream(fOutput);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
		} else if (fOutput.getName().length() != 0) {
			
			try {

				out = new FileOutputStream(fOutput.getName());
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		} else {
			
			File fCf = null;
			
			fCf = fOutput;
			
			try {
				out = new FileOutputStream(fCf);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
				
		try {
			out.write(bConfiguration);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		    	
		return true;
		
	}
	
	/**
	 * 	
	 * @return the ConfigurationFile Type per the ConfigurationFile() field statics */
	public int getConfigurationFileType () {
		
		if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
			return ConfigurationFile.PKT_CBL_VER_20;
		} else {
			return ConfigurationFile.DOCSIS_VER_31;
		}
		
	}
	
	/**
	 * 
	 * @param boolDottextOutputFormat TRUE = Textual OID Output , FALSE = Dotted OID Output */
	public void setDotTextOIDOutputFormat(boolean boolDottextOutputFormat) {
		this.boolDottextOutputFormat = boolDottextOutputFormat;
	}
	
	/**
	 * DEFAULT = true;
	 * @deprecated
	 * @param boolVerboseExport*/
	public void setExportVerbose(boolean boolVerboseExport) {
		this.boolVerboseExport = boolVerboseExport;
	}
	
	/* *******************************************************************************
	 * 								Private Methods
	 ********************************************************************************/
	
	/**
	 * 
	 */
 	private void tlvToDictionary () {
		
		boolean localDebug = Boolean.FALSE;
		
		String sDictionaryTableName;
		
		//Starting Container for holding a DATA_TYPE_MULTI_TLV_BYTE_ARRAY
		TlvBuilder tbTopLevelTLV = new TlvBuilder();
		
		//Container for holding a DATA_TYPE_MULTI_TLV_BYTE_ARRAY
		TlvBuilder tbMultiTlvByteArray = new TlvBuilder();
		
		//Container for holding MultiTlvByteArray
		TlvVariableBinding tvbMultiTlvByteArray = null;
		
		//List to start JSON Array Objects
		aljoTopLevelTlvDictionary = new ArrayList<JSONObject>();
		
		//Create a TLV Builder
		TlvBuilder tbTLV = new TlvBuilder();
		
		if (iConfigurationFileType == DOCSIS_PKTCBL) {
			//Determine what kind of file: DOCSIS or PACKET CABLE
			if (bTLV[0] == PacketCableConstants.FILE_MARKER) {
				
				if (debug|localDebug) System.out.println("ConfigrationFileExport.tlvToDictionary() -> PACKET-CABLE-FILE-FOUND");
				
				sDictionaryTableName = DictionarySQLConstants.PACKET_CABLE_DICTIONARY_TABLE_NAME;
				
			} else {
				
				if (debug|localDebug) System.out.println("ConfigrationFileExport.tlvToDictionary() -> DOCSIS-CABLE-FILE-FOUND");
				
				sDictionaryTableName = DictionarySQLConstants.DOCSIS_DICTIONARY_TABLE_NAME;			
			}
			
			//Get Top Level TLV Dictionary
			dsqDictionarySQLQueries = new DictionarySQLQueries(sDictionaryTableName);
		} else {
			sDictionaryTableName = dsqDictionarySQLQueries.getDictionaryTableName();
		}

		
		//Build Map to determine the Type to ByteLength Mapping
		Map<Integer,Integer> miiTopLevelTLV = dsqDictionarySQLQueries.getTopLevelByteLength();

		//Add TLV Byte Array from Constructor for later processing
		tbTLV.add(new TlvVariableBinding(bTLV,miiTopLevelTLV));
		
		if (debug|localDebug) 
			System.out.println(	"ConfigrationFileExport.tlvToDictionary() -> " +
					"miiTopLevelTLV.size(): " + miiTopLevelTLV.size() + " -> " +
					"tbTLV.length(): " + tbTLV.length() + " -> " +
					"bTLV.length: " + bTLV.length);
				
		int 	iTlvType = 0 , 
				iMultiTlvByteArrayType = RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH;
	
		if (debug|localDebug) {
			System.out.println("ConfigrationFileExport.tlvToDictionary() -> miiTopLevelTLV: " + miiTopLevelTLV);
			
			if (tbTLV.toByteArray()[0] == PacketCableConstants.FILE_MARKER) {
				
				try {
					
					for (HexString hs : PacketCableCompiler.getTopLevelTlvToHexStringList(tbTLV.toByteArray())) {
						System.out.println("TLV-HEX: " + hs.toString(":"));
					}
						
				} catch (TlvException e) {
					e.printStackTrace();
				}
			}
			
		}
		
		//Sort by TopLevel TLVs via List<byte[]>
		for (byte[] bTopLevelTLV : tbTLV.sortByTopLevelTlv(miiTopLevelTLV)) {
			
			//Get TLV Type 
			iTlvType = BinaryConversion.byteToUnsignedInteger(bTopLevelTLV[0]);
			
			if (debug|localDebug)
				System.out.println("ConfigrationFileExport.tlvToDictionary() -> TLV-TYPE: " + iTlvType);

			//Check for DOCSIS EOF - Reached end of file (EOF)
			if (iTlvType == BinaryConversion.byteToUnsignedInteger(DocsisConstants.EOF)) {break;}
			
			//Get TLV Dictionary for iTlvType
			JSONObject joDictionaryTemp = dsqDictionarySQLQueries.getTlvDefinition(iTlvType);
		
			//Check for TLV's that are DATA_TYPE_MULTI_TLV_BYTE_ARRAY
			try {
				
				//Check for TLV's that are DATA_TYPE_MULTI_TLV_BYTE_ARRAY and make sure that we are not combining  different TLV Types
				if (joDictionaryTemp.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_MULTI_TLV_BYTE_ARRAY) && 
						(joDictionaryTemp.getInt(Dictionary.TYPE) == iTlvType)) {
					
					// If this is a -1, that means that this is the first time it saw a DATA_TYPE_MULTI_TLV_BYTE_ARRAY
					if ((iMultiTlvByteArrayType == RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH) || (iMultiTlvByteArrayType == iTlvType)) {				
						
						//Set
						iMultiTlvByteArrayType = iTlvType;
						
						//Build DATA_TYPE_MULTI_TLV_BYTE_ARRAY
						try {						
							tbMultiTlvByteArray.add(iTlvType,TlvBuilder.getTlvValue(bTopLevelTLV));
						} catch (TlvException e) {
							e.printStackTrace();
						}
						
						//Goto next TLV to get the next contiguous DATA_TYPE_MULTI_TLV_BYTE_ARRAY
						continue;
					
					//There may be a condition that 2 different DATA_TYPE_MULTI_TLV_BYTE_ARRAY are contiguous 	
					} else if (iMultiTlvByteArrayType != iTlvType) {												
						iMultiTlvByteArrayType = RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH;					
					}
					
				//Create a TlvBuilder for the Top Level TLV that is not a DATA_TYPE_MULTI_TLV_BYTE_ARRAY
				} else {
					tbTopLevelTLV = new TlvBuilder();					
				}
				
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
						
			//At this point we may need to process the current type and the DATA_TYPE_MULTI_TLV_BYTE_ARRAY
			if (iMultiTlvByteArrayType != RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH) {
				
				if (debug|localDebug) 
					System.out.println("ConfigrationFileExport.tlvToDictionary()" +
										" - End Of MULTI_TLV_BYTE_ARRAY" + 
										" - Current Type: " + iTlvType + 
										" - Process MULTI_TLV_BYTE_ARRAY Type: " + iMultiTlvByteArrayType);
								
				//Compile total TLV Value and send to byte Array
				byte[] bMultiTlvByteArray = TlvBuilder.coupleMultipleTopLevelTlvValues(tbMultiTlvByteArray);
				
				//Create new TlvVariableBinding
				tvbMultiTlvByteArray = new TlvVariableBinding();
				
				//Add
				try {
					tvbMultiTlvByteArray.add(iMultiTlvByteArrayType, bMultiTlvByteArray);
				} catch (TlvException e) {
					e.printStackTrace();
				}

				if (debug|localDebug) 
					System.out.println("ConfigrationFileExport.tlvToDictionary()" +
										" - TlvVariableBinding HEX: " + tvbMultiTlvByteArray.toString());
				
				if (debug|localDebug) System.out.println("++++++++HERE++++++++");
				
				//Get TopLevelTLV TlvDisassemble
				TlvDisassemble tdTopLevelTLV = new TlvDisassemble(tvbMultiTlvByteArray,sDictionaryTableName);
				
				//Get TopLevelTLV Dictionary and insert into Array List
				try {
					if (debug|localDebug)
						System.out.println("ConfigrationFileExport.tlvToDictionary() " + tdTopLevelTLV.getTlvDictionary().getJSONObject(0).toString());
					
					aljoTopLevelTlvDictionary.add(tdTopLevelTLV.getTlvDictionary().getJSONObject(0));
					
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				//Reset iMultiTlvByteArrayType
				iMultiTlvByteArrayType = RESET_DATA_TYPE_MULTI_TLV_BYTE_ARRAY_SEARCH;
				
			} 
			
			//If this is not a DATA_TYPE_MULTI_TLV_BYTE_ARRAY , its only a single Top Level TLV		
			tbTopLevelTLV.add(new TlvVariableBinding(bTopLevelTLV,miiTopLevelTLV));
			
			//Get TopLevelTLV TlvDisassemble
			TlvDisassemble tdTopLevelTLV = new TlvDisassemble(tbTopLevelTLV,sDictionaryTableName);
			
			//Get TopLevelTLV Dictionary and insert into Array List
			try {
				if (debug|localDebug)
					System.out.println("ConfigrationFileExport.tlvToDictionary() " + tdTopLevelTLV.getTlvDictionary().getJSONObject(0).toString());
				
				aljoTopLevelTlvDictionary.add(tdTopLevelTLV.getTlvDictionary().getJSONObject(0));
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
				
	}
	
	/**
	 * 
	 * @param joTopLevelTLV
	 * @param iIndentation
	 * @return StringBuilder
	 * @throws JSONException */
	private StringBuilder topLevelTLVCodeBlock (JSONObject joTopLevelTLV, int iIndentation) throws JSONException {
		
		StringBuilder sbTopLevelTLVCodeBlock = new StringBuilder();

		StringBuilder sbIndentation = new StringBuilder();
				
		if ((!this.boolVerboseExport) && (!(joTopLevelTLV.has(Dictionary.VALUE)))) {	
			return sbTopLevelTLVCodeBlock;
		}
		
		for (int iNumIndent = 1 ; iNumIndent < iIndentation; iNumIndent++)
			sbIndentation.append('\t');
					
		//If the Value contain a JSON Array, it can only be a OID
		if ((joTopLevelTLV.has(Dictionary.VALUE)) && (JSONTools.containJSONArray(joTopLevelTLV,Dictionary.VALUE))) {
			
			JSONArray jaTopLevelTLVOID = joTopLevelTLV.getJSONArray(Dictionary.VALUE);
			
			jaTopLevelTLVOID.getJSONObject(0).getString("OID");
			
			//Check to see if this is a 2 byte VarBind, if so convert from ASCII to HexSting
			if (joTopLevelTLV.getInt(Dictionary.BYTE_LENGTH) < 2) {
								
				sbTopLevelTLVCodeBlock	.append('\t')
										.append(joTopLevelTLV.get(Dictionary.TLV_NAME))
										.append(' ')
										.append(NetSNMP.toOIDFormat(jaTopLevelTLVOID.getJSONObject(0).getString("OID"),boolDottextOutputFormat))
										.append(' ')
										.append(BER_DATA_TYPE.get(Integer.decode(jaTopLevelTLVOID.getJSONObject(0).getString("DATA_TYPE"))))
										.append(" \"")
										.append(jaTopLevelTLVOID.getJSONObject(0).getString("VALUE"))
										.append("\";")
										.append("\t /* TLV: ")
										.append(joTopLevelTLV.get(Dictionary.PARENT_TYPE_LIST).toString().replace("-1,", "").replaceAll("," , "."))
										.append("*/\n");				
			} else {
								
				sbTopLevelTLVCodeBlock	.append('\t')
										.append(joTopLevelTLV.get(Dictionary.TLV_NAME))
										.append(' ')
										.append(NetSNMP.toOIDFormat(jaTopLevelTLVOID.getJSONObject(0).getString("OID"),boolDottextOutputFormat))
										.append(' ')
										.append(BER_DATA_TYPE.get(Integer.decode(jaTopLevelTLVOID.getJSONObject(0).getString("DATA_TYPE"))))
										.append(" \"")
										.append(HexString.asciiToHex(jaTopLevelTLVOID.getJSONObject(0).getString("VALUE"),":"))
										.append("\";")
										.append("\t /* TLV: ")
										.append(joTopLevelTLV.get(Dictionary.PARENT_TYPE_LIST).toString().replace("-1,", "").replaceAll("," , "."))
										.append("*/\n");				
			}
			
		//Top Level TLV Only
		} else {
			
			if (joTopLevelTLV.has(Dictionary.VALUE)) {
				sbTopLevelTLVCodeBlock	.append('\t')
										.append(joTopLevelTLV.get(Dictionary.TLV_NAME))
										.append(' ')
										.append(joTopLevelTLV.get(Dictionary.VALUE))
										.append(';')
										.append("\t /* TLV: ")
										.append(joTopLevelTLV.get(Dictionary.PARENT_TYPE_LIST).toString().replace("-1,", "").replaceAll("," , "."))
										.append("*/\n");				
			} else {
				
				if ((joTopLevelTLV.get(Dictionary.TLV_NAME).equals("Snmp11")) || (joTopLevelTLV.get(Dictionary.TLV_NAME).equals("Snmp64"))) {
					
					if (debug) {
						System.out.println("++++++++++++++++++++++Ignoring Snmp11 || Snmp64 +++++++++++++++++++++++++++++++++++++");
					}
					
					return sbTopLevelTLVCodeBlock;
				}
				
				sbTopLevelTLVCodeBlock	.append('\t')
										.append(joTopLevelTLV.get(Dictionary.TLV_NAME))
										.append(' ').append(';')
										.append("\t /* TLV: ")
										.append(joTopLevelTLV.get(Dictionary.PARENT_TYPE_LIST).toString().replace("-1,", "").replaceAll("," , "."))
										.append("*/\n");				
			}
			
		}
				
		return sbTopLevelTLVCodeBlock;
	}

	/**
	 * 
	 * @param jaTopLevelTLV
	 * @param iIndentation
	
	
	 * @return StringBuilder
	 * @throws JSONException */
	private StringBuilder topLevelTLVCodeBlock (JSONArray jaTopLevelTLV , int iIndentation) throws JSONException {
		
		boolean localDebug = Boolean.FALSE;
		
		StringBuilder sbTopLevelTLVCodeBlock = new StringBuilder();
		
		StringBuilder sbIndentation = new StringBuilder();		
		for (int iNumIndent = 1 ; iNumIndent < iIndentation; iNumIndent++)
			sbIndentation.append('\t');

		for (int iJsonArrayIndex = 0 ; iJsonArrayIndex < jaTopLevelTLV.length() ; iJsonArrayIndex++ ) {

			JSONObject joTopLevelTLVLocal = null;

			try {
				joTopLevelTLVLocal = jaTopLevelTLV.getJSONObject(iJsonArrayIndex);
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
			
			try {
							
				if (joTopLevelTLVLocal.getBoolean(Dictionary.ARE_SUBTYPES)) {
				
					sbTopLevelTLVCodeBlock
						.append(sbIndentation).append('\t')
						.append(joTopLevelTLVLocal.get(Dictionary.TLV_NAME))
						.append(" {\n");
					
					if (debug|localDebug)
						System.out.println("topLevelTLVCodeBlock(ja): " + joTopLevelTLVLocal.getJSONArray(Dictionary.SUBTYPE_ARRAY).toString());
					
					sbTopLevelTLVCodeBlock.append(topLevelTLVCodeBlock(joTopLevelTLVLocal.getJSONArray(Dictionary.SUBTYPE_ARRAY),iIndentation+1));
					
				} else {					
					sbTopLevelTLVCodeBlock	.append(sbIndentation)
											.append(topLevelTLVCodeBlock(joTopLevelTLVLocal,iIndentation));
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
		}
		
		sbTopLevelTLVCodeBlock	.append(sbIndentation)
								.append("} " + END_OF_CODE_BLOCK + "\n\n");
		
		return sbTopLevelTLVCodeBlock;
	}

	/**
	 * */
	private void initBER() {
		BER_DATA_TYPE.put((int) BER.COUNTER32,   "Counter32");
		BER_DATA_TYPE.put((int) BER.COUNTER64,   "Counter64");
		BER_DATA_TYPE.put((int) BER.GAUGE32,     "Gauge32");
		BER_DATA_TYPE.put((int) BER.INTEGER32,   "Integer32");
		BER_DATA_TYPE.put((int) BER.TIMETICKS,   "TimeTicks");
		BER_DATA_TYPE.put((int) BER.IPADDRESS,   "IpAddress");
		BER_DATA_TYPE.put((int) BER.OCTETSTRING, "OctetString");
		
		//This Type does not exists as a SNMP DataType, this is only for use in this program
		BER_DATA_TYPE.put(BinaryConversion.byteToUnsignedInteger(BERService.HEX), "HexString");
	}
	
	/**
	 * 
	 */
	private void init() {
				
		initBER();
		
		//Figure Out the Configuration file Type DOCSIS vs. PacketCable
		
		if (bTLV[0] != PacketCableConstants.FILE_MARKER ) {
			sConfigurationFileStart = "Docsis";
		} else {
			sConfigurationFileStart = "PacketCable-X.X";
		}
	}
	
	/**
	 * Only Non DOCSIS and PacketCable files*/
	private void init(int iConfigurationFileType) {
		
		initBER();
			
		if ((iConfigurationFileType >= DPOE_VER_10) && 
				(iConfigurationFileType <= DPOE_VER_20)) {
			
			removeNonDictionaryTopLevelTLV();
			
			sConfigurationFileStart = "DPoE";
		}
	}
	
	/**
	 * 
	
	 * @return byte[]
	 */
	private byte[] docsisPsuedoTLVArray() {
		
		boolean localDebug = Boolean.FALSE;
		
		ByteArrayOutputStream baosTLV = new ByteArrayOutputStream();
		
		byte bByte[] = {0x01, 0x01, 0x00};
		
		for (int iIndex = 0 ; iIndex < 255 ; iIndex++) {
			
			if (localDebug|debug)
				System.out.println("docsisPsuedoTLVArray() - Index: " + iIndex);

			if (localDebug|debug)
				System.out.println("docsisPsuedoTLVArray()" + new HexString(baosTLV.toByteArray()).toString());
		
			try {
				baosTLV.write(bByte);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			bByte[0]++;
		}
		
		if (localDebug|debug)
			System.out.println("docsisPsuedoTLVArray()" + new HexString(baosTLV.toByteArray()).toString());
		
		return baosTLV.toByteArray();
	}
	
	/**
	 * 
	
	 * @return byte[]
	 */
	private byte[] packetCablePsuedoTLVArray() {
		
		ByteArrayOutputStream baosTLV = new ByteArrayOutputStream();
		
		byte bByte[] = {PacketCableConstants.FILE_MARKER, 0x01, 0x00};
		
		for (int iIndex = 0 ; iIndex < 255 ; iIndex++) {
			try {
				baosTLV.write(bByte);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (bByte[0] == PacketCableConstants.FILE_MARKER) {
				bByte[0] = 0x01;
			} else {
				bByte[0]++;
			}
			
		}
		
		return baosTLV.toByteArray();
	}

	/**
	 * 
	 * @param jaDict
	 */
	private void convertJSONArrayDictToJSONObjectArrayList (JSONArray jaDict) {
		
		boolean localDebug = Boolean.FALSE;
		
		aljoTopLevelTlvDictionary = new ArrayList<JSONObject>();
		
		for (int iIndex = 0 ; iIndex < jaDict.length() ; iIndex++) {
			try {
				
				if (localDebug|debug)
					System.out.println("convertJSONArrayDictToJSONObjectArrayList() - Index: " + iIndex + " -> " + jaDict.getJSONObject(iIndex).toString());
			
				aljoTopLevelTlvDictionary.add(jaDict.getJSONObject(iIndex));
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		if (localDebug|debug)
			System.out.println("convertJSONArrayDictToJSONObjectArrayList() - ArraySize: " + aljoTopLevelTlvDictionary.size());

		
	}
	
	/**
	 * Method banner.
	 * @return StringBuilder
	 */
	private StringBuilder banner() {
		
		StringBuilder sbBanner = new StringBuilder();
		
		sbBanner.append("/*\n");
		
		sbBanner.append(Constants.APACHE_20_LICENCE_DISCLAIMER + "\n\n");
		
		sbBanner.append("\tSnmp11 OID DataType Value\n");
		
		if (this.iConfigurationFileType > DOCSIS_VER_31)
			sbBanner.append("\tSnmp64 OID DataType Value\n\n");
		else 
			sbBanner.append('\n');
		
		sbBanner.append("\tDOUBLE_BYTE_ARRAY_FORMAT: " + DataTypeFormatConversion.DOUBLE_BYTE_ARRAY_FORMAT + "\n");
		sbBanner.append("\tIPV4_TRANSPORT_FORMAT: " + DataTypeFormatConversion.IPV4_TRANSPORT_FORMAT + "\n");
		sbBanner.append("\tIPV6_TRANSPORT_FORMAT: " + DataTypeFormatConversion.IPV6_TRANSPORT_FORMAT + "\n");
		sbBanner.append("\tSTRING_BITS_FORMAT: " + DataTypeFormatConversion.STRING_BITS_FORMAT + "\n");
		sbBanner.append("\tIPV6_ADDRESS_FORMAT: " + DataTypeFormatConversion.IPV6_ADDRESS_FORMAT + "\n");
		sbBanner.append("\tIPV4_ADDRESS_FORMAT: " + DataTypeFormatConversion.IPV4_ADDRESS_FORMAT + "\n");
		sbBanner.append("\tMAC_ADDRESS_FORMAT: " + DataTypeFormatConversion.MAC_ADDRESS_FORMAT + "\n\n");
				
		sbBanner.append("*/\n\n");
		
		return sbBanner;
	}
	
	/**
	 * 
	 * @param joTlvDictionary - JSON OBject of the Dictionary
	 * @return the Hint for the datatype that is used
	 */
	private String getDisplayHint(JSONObject joTlvDictionary) {
		
		String sDisplayHint = "";
		
		try {
			if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_DOUBLE_BYTE_ARRAY)) {
				
				sDisplayHint = DataTypeFormatConversion.DOUBLE_BYTE_ARRAY_FORMAT;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_TRANSPORT_ADDR_IPV4_ADDR)) {
				
				sDisplayHint = DataTypeFormatConversion.IPV4_TRANSPORT_FORMAT;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_TRANSPORT_ADDR_IPV6_ADDR)) {
				
				sDisplayHint = DataTypeFormatConversion.IPV6_TRANSPORT_FORMAT;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_STRING_BITS)) {
				
				sDisplayHint = DataTypeFormatConversion.STRING_BITS_FORMAT;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_BYTE_ARRAY_IPV4_ADDR)) {
				
				sDisplayHint = DataTypeFormatConversion.IPV4_ADDRESS_FORMAT;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_BYTE_ARRAY_IPV6_ADDR)) {
				
				sDisplayHint = DataTypeFormatConversion.IPV6_ADDRESS_FORMAT;
			
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_OID)) {
				
				sDisplayHint = "docsDevFilterLLCUnmatchedAction.0 Integer32 \"1\" \n " +
								"vacmAccessStorageType.'readwritegroup'.''.2.noAuthNoPriv Integer \"2\"" +
								"1.3.6.1.2.1.69.1.3.5 Integer32 \"1\"" ;
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_INTEGER)) {
				
				sDisplayHint = "Range: -2,147,483,648 to 2,147,483,647";
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_STRING)) {
				
				sDisplayHint = "Any ASCII String";
			
			}  else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_STRING_NULL_TERMINATED)) {
			
				sDisplayHint = "Any ASCII String with a Terminating NULL at the end /0 or 0x00";
			
			}  else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_BYTE_ARRAY)) {
			
				sDisplayHint = "xx:xx:xx......xx:xx:xx - Example: 01:23:34:56:78:9a:bc:cd:ef";
			
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_OID_ASN1_OBJECT_6)) {
				
				sDisplayHint = 	"docsDevFilterLLCUnmatchedAction.0 \n " +
								"vacmAccessStorageType.'readwritegroup'.''.2.noAuthNoPriv \n" +
								"1.3.6.1.2.1.69.1.3.5 " ;
				
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_BYTE)) {
				
				sDisplayHint = 	"Byte: xx Example ff" ;
				
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_MAC_ADDRESS)) {
				
				sDisplayHint = 	DataTypeFormatConversion.MAC_ADDRESS_FORMAT ;
				
			} else if (joTlvDictionary.getString(Dictionary.DATA_TYPE).equals(DataTypeDictionaryReference.DATA_TYPE_TRANSPORT_ADDR_INET_ADDR)) {
				
				sDisplayHint = 	DataTypeFormatConversion.IPV4_TRANSPORT_FORMAT + " \n\nOR\n\n" + DataTypeFormatConversion.IPV6_TRANSPORT_FORMAT;
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return sDisplayHint;
	}
	
	/**
	 * This method will remove all TopLevel TLV that are not defined in the Dictionary
	 * Currently support 1 byte Length TLVs
	 */
	private void removeNonDictionaryTopLevelTLV() {
		
		Boolean localDebug = Boolean.FALSE;
		
		/* Get TopLevel List*/ 
		List<Integer> liTopLevelDict = dsqDictionarySQLQueries.getTopLevelTLV();
		
		List<Integer> liTopLevelCFE = null;
		
		try {
			liTopLevelCFE = getTlvBuilder().getTopLevelTlvList();
		} catch (TlvException e) {
			e.printStackTrace();
		}
		
		/*This will create a single instance of each Type */
		liTopLevelCFE = new ArrayList<Integer>(new LinkedHashSet<Integer>(liTopLevelCFE));
		
		/*Remove Types that are not suppose to be There */
		liTopLevelCFE.retainAll(liTopLevelDict);
				
		if(debug|localDebug) {
			System.out.println("removeNonDictionaryTopLevelTLV() -> DICT: " + liTopLevelDict);
			System.out.println("removeNonDictionaryTopLevelTLV() -> CFE remove DICT: " + liTopLevelCFE);
		}
		
		/*Create new ByteArray*/
		bTLV = TlvBuilder.fetchTlv(liTopLevelCFE, bTLV);
		
	}

}
