package com.feedbooks.opds;

import org.xml.sax.InputSource;
import org.xml.sax.*;
import org.xml.sax.helpers.*;


import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class OPDSRequirementsValidator {


	public boolean validate(InputSource l)  {
		try{

			XMLFilter[] filters={
				/*Very high priority*/
				new OPDSRequirementLink(),new OPDSRequirementAcquisitionType(), new OPDSRequirementSearchRel(),new OPDSRequirementAcquisitionOrNavigation(),
				/*High priority*/
				new OPDSRequirementPlainTextSummary(), new OPDSRequirementImageRel(), new OPDSRequirementImageBitmap(), new OPDSRequirementDublinCore()
			};

			XMLFilter tail = filters[0];
			XMLFilter head = tail;


			for(int i=1;i<filters.length;i++ ) {
				XMLFilter new_tail = filters[i];
				tail.setParent(new_tail);
				tail=new_tail;
			}

			tail.setParent(XMLReaderFactory.createXMLReader());
			if (getErrorHandler()!=null) {
				head.setErrorHandler(getErrorHandler());
			}else{
				DefaultHandler handler = new DefaultHandler();
				head.setErrorHandler(handler);
			}
			head.parse(l);
			return true;
		}catch(Exception e){
			System.err.println(e);
		}
		return false;
	}

	private ErrorHandler eh;

	public void setErrorHandler(ErrorHandler e){
		eh=e;
	}

	public ErrorHandler getErrorHandler(){
		return eh;
	}

}

class OPDSRequirementFilter extends XMLFilterImpl {
	Locator locator;
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
		super.setDocumentLocator(locator);
	}


	protected Locator getLocator(){
		return this.locator;
	}

	protected String getLocationString(){
		if(locator!=null){
			return ""+locator.getLineNumber()+":"+locator.getColumnNumber();
		}
		return "";
	}

}

/* REQUIREMENT : Each catalog entry must have a link  */
class OPDSRequirementLink extends OPDSRequirementFilter {

	private int links;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			links=0;
		}
		if(name.equalsIgnoreCase("link")){links++;}

		super.startElement(uri,name,qName,atts);
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("entry") && links==0){
			error(new SAXParseException("Every entry MUST have a link",getLocator()))	;
		}

		super.endElement(uri,name,qName);
	}
}

/* REQUIREMENT : Each acquisition link must have a type */
class OPDSRequirementAcquisitionType extends OPDSRequirementFilter {

	private boolean in_entry;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=true;
		}
		if(in_entry){
			if(name.equalsIgnoreCase("link")){
				String rel=atts.getValue("rel");
				String type=atts.getValue("type");

				if(rel!=null && rel.contains("opds-spec.org/acquisition") && (type==null || !type.contains("/"))){
					error(new SAXParseException("Every acquisition link MUST have type",getLocator()));
				}
			}
		}

		super.startElement(uri,name,qName,atts);
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=false;
		}

		super.endElement(uri,name,qName);
	}
}

/* REQUIREMENT : Search links must use the rel search and OpenSearch mimetype */
class OPDSRequirementSearchRel extends OPDSRequirementFilter {

	private boolean in_entry;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=true;
		}
		if(!in_entry){
			if(name.equalsIgnoreCase("link")){
				String rel=atts.getValue("rel");
				String type=atts.getValue("type");

				if(rel!=null && rel.contains("search") && (type==null || !type.contains("application/opensearchdescription+xml"))){
					error(new SAXParseException("Search link MUST use opensearch mimetype",getLocator()));
				}
			}
		}

		super.startElement(uri,name,qName,atts);
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=false;
		}

		super.endElement(uri,name,qName);
	}
}

/* REQUIREMENT : A feed can't be a navigation & acquisition feed at the same time */
class OPDSRequirementAcquisitionOrNavigation extends OPDSRequirementFilter {

	private boolean in_entry;
	private boolean typed;
	private boolean acquisition_feed;
	private int acquisition_links_count=0;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=true;
			acquisition_links_count=0;
		}
		if(in_entry){
			if(name.equalsIgnoreCase("link")){
				String rel=atts.getValue("rel");
				if(rel!=null && rel.contains("opds-spec.org/acquisition")){
					setAcquisitionFeed();
					acquisition_links_count++;
				}
			}
		}

		super.startElement(uri,name,qName,atts);
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=false;
			if(!isTyped()){
				if(acquisition_links_count > 0){
					setAcquisitionFeed();
				}else{
					setNavigationFeed();
				}
			}else{
				if(isAcquisitionFeed() && acquisition_links_count == 0){
					error(new SAXParseException("Feed CAN'T be an acquisition and navigation feed (no acquisition link found)",getLocator()));
				}
				if(isNavigationFeed() && acquisition_links_count > 0){
					error(new SAXParseException("Feed CAN'T be an acquisition and navigation feed (found an acquisition link in a navigation feed)",getLocator()));
				}

			}
		}

		super.endElement(uri,name,qName);
	}

	private void setAcquisitionFeed()
	{
		acquisition_feed=true;
		typed=true;
	}

	private void setNavigationFeed(){
		acquisition_feed=false;
		typed=true;
	}

	private boolean isTyped(){
		return typed;
	}

	private boolean isAcquisitionFeed(){
		return isTyped() && acquisition_feed;
	}

	private boolean isNavigationFeed(){
		return isTyped() && !acquisition_feed;
	}

}

/* REQUIREMENT : atom:summary must be plain text */
class OPDSRequirementPlainTextSummary extends OPDSRequirementFilter {

	private boolean in_summary;
	private StringBuffer summary;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(in_summary){
			error(new SAXParseException("Summary MUST be plain text",getLocator()));
		}
		if(name.equalsIgnoreCase("summary")){
			in_summary=true;
			summary=new StringBuffer();
		}

		super.startElement(uri,name,qName,atts);
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		if(in_summary){
			summary.append(ch,start,length);
			super.characters( ch, start, length);
		}
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("summary")){
			in_summary=false;
			if (Pattern.matches(".*<.*>.*",summary)){ 
				error(new SAXParseException("Summary MUST be plain text",getLocator()));
			}
		}

		super.endElement(uri,name,qName);
	}
}


/* REQUIREMENT : Catalogs should use the right rel for images  */
class OPDSRequirementImageRel extends OPDSRequirementFilter {

	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("link")){
			String rel=atts.getValue("rel");

			if(rel!=null && (rel.contains("http://opds-spec.org/cover") || rel.contains("http://opds-spec.org/thumbnail") || rel.contains("x-stanza-cover-image"))){
				error(new SAXParseException("Images MUST use valid OPDS 1.0+ rel (http://opds-spec.org/image...)",getLocator()));
			}
		}

		super.startElement(uri,name,qName,atts);
	}

}

/* REQUIREMENT : Linked image resources must be a bitmap  */
class OPDSRequirementImageBitmap extends OPDSRequirementFilter {

	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("link")){
			String rel=atts.getValue("rel");
			String type=atts.getValue("type");

			if(rel!=null && (rel.contains("http://opds-spec.org/image") && (type==null || (
								!type.contains("image/png") &&
								!type.contains("image/jpeg") &&
								!type.contains("image/jpg") &&
								!type.contains("image/gif") &&
								!type.contains("image/bmp")
								) ) ) ){
				error(new SAXParseException("Images MUST be bitmaps",getLocator()));
								}
		}

		super.startElement(uri,name,qName,atts);
	}

}

/* REQUIREMENT : Check for dc elements usages that should only be represented with Atom (dc:creator, dc:title, dc:subject) */
class OPDSRequirementDublinCore extends OPDSRequirementFilter {

	private boolean in_entry;
	private boolean creator;
	private boolean title;
	private boolean subject;
	private boolean atom_title;
	private boolean atom_author;
	private boolean atom_category;
	public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=true;
		}
		if(in_entry){
			if(uri.contains("dc")){
				if(name.equalsIgnoreCase("creator")){
					creator=true;
				}
				if(name.equalsIgnoreCase("subject")){
					subject=true;
				}
				if(name.equalsIgnoreCase("title")){
					System.err.println("FOIND dc:tit");

					title=true;
				}
			}else{
				if(name.equalsIgnoreCase("title")){
					atom_title=true;
				}
				if(name.equalsIgnoreCase("author")){
					atom_title=true;
				}
				if(name.equalsIgnoreCase("category")){
					atom_category=true;
				}
			}
		}

		super.startElement(uri,name,qName,atts);
	}

	public void endElement (String uri, String name, String qName) throws SAXException
	{
		if(name.equalsIgnoreCase("entry")){
			in_entry=false;

			if(title && !atom_title){
				error(new SAXParseException("entries MUST use atom:title instead of dc:title",getLocator()));
			}
			if(subject && !atom_category){
				error(new SAXParseException("entries MUST use atom:category instead of dc:subject",getLocator()));
			}
			if(creator && !atom_author){
				error(new SAXParseException("entries MUST use atom:author instead of dc:creator",getLocator()));
			}

			creator=false;
			title=false;
			subject=false;
			atom_category=false;
			atom_title=false;
			atom_author=false;
		}

		super.endElement(uri,name,qName);
	}

}