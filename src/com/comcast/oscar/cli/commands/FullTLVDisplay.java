package com.comcast.oscar.cli.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;

import com.comcast.oscar.configurationfile.ConfigurationFileExport;

/**
 * 
 * @author Allen Flickinger (allen.flickinger@gmail.com)
 * 
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

 */

public class FullTLVDisplay {
	
	private boolean boolPrettyPrint = true;

	/**
	 * Set option parameters for command Full TLV display
	 * @return Option
	 */
	public static final Option OptionParameters() 
	{
		OptionBuilder.withValueSeparator(' ');
		OptionBuilder.withLongOpt("fulltlvdisplay");
    	OptionBuilder.withDescription("Display all TLVs available in the dictionary for the defined specification.");
    	return OptionBuilder.create("ftd");
	}
	
	/**
	 * Print Full TLV Display
	 * @param configurationFileType
	 */
	public void printFullTLVDisplay(int configurationFileType) 
	{
		ConfigurationFileExport cfe = new ConfigurationFileExport(configurationFileType);
		System.out.println(cfe.toPrettyPrint(this.boolPrettyPrint));
	}
}
