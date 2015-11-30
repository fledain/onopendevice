
var g_databasesTree=null;
var g_recordsTree=null;
var oDiff=null;
var g_Started=0;
var g_Timeout=10000;
var g_ProgressURL = "http:/tools/record/list";
var g_databasesURL = "http:/tools/db";
var g_VSConfigURL = "http:/tools/vsconfig";
var g_recordURL = "http:/tools/record";
var g_LogcatURL = "http:/tools/logcat";
var g_QueryURL = "http:/tools/query";
var g_ScreenshotURL = "device/screenshot";
var g_ScreenshotsListURL = "http:/device/screenshotslist";
var g_RefreshingState = 0;
var g_nodeurl;
var g_currentOpenedDb=0;
var g_selectedRecords = [];
var g_selectedPackage;
var g_VSConfigTextData;
var g_ExportedData = [];
var g_WEB = document.location.href.substr(0,4) == "http";

kmrSimpleTabs.addLoadEvent(ood_onload);

function getImageData(uri)
{
	return g_WEB ? uri : (getExportedData(uri) || uri);
}

function ood_onload()
{	

	var img;
	document.getElementById("logo").src=getImageData("app/img/logo.png");
	document.getElementById("query_open").src=getImageData("app/img/imgfolder.gif");
	document.getElementById("cal_forward").src=getImageData("app/img/cal_forward.gif");
	document.getElementById("cal_reverse").src=getImageData("app/img/cal_reverse.gif");
	document.getElementById("cal_display").src=getImageData("app/img/cal.gif");

	if(!g_WEB)
	{
/*
		for(var id of ["export_button", 
					   "logcat_display", "logcat_record", "logcat_clear",
					   "refreshCONTACTS","refreshRAW_CONTACTS","refreshDATA","refreshGROUPS","refreshALL",
					   "vsconfig_save"])
		{
			document.getElementById(id).style.display="none";
		}
*/
		document.getElementById("export_button").style.display="none";
		document.getElementById("logcat_display").style.display="none";
		document.getElementById("logcat_record").style.display="none";
		document.getElementById("logcat_clear").style.display="none";
		document.getElementById("refreshCONTACTS").style.display="none";
		document.getElementById("refreshRAW_CONTACTS").style.display="none";
		document.getElementById("refreshDATA").style.display="none";
		document.getElementById("refreshGROUPS").style.display="none";
		document.getElementById("refreshALL").style.display="none";
		document.getElementById("vsconfig_save").style.display="none";
	}
	
	redrawQueryForm();
	refreshNextState();
}

function loadDTreeImages(dtree)
{
	dtree.icon.root=getExportedData(dtree.icon.root);
	dtree.icon.folder=getExportedData(dtree.icon.folder);
	dtree.icon.folderOpen=getExportedData(dtree.icon.folderOpen);
	dtree.icon.node=getExportedData(dtree.icon.node);
	dtree.icon.empty=getExportedData(dtree.icon.empty);
	dtree.icon.line=getExportedData(dtree.icon.line);
	dtree.icon.join=getExportedData(dtree.icon.join);
	dtree.icon.joinBottom=getExportedData(dtree.icon.joinBottom);
	dtree.icon.plus=getExportedData(dtree.icon.plus);
	dtree.icon.plusBottom=getExportedData(dtree.icon.plusBottom);
	dtree.icon.minus=getExportedData(dtree.icon.minus);
	dtree.icon.minusBottom=getExportedData(dtree.icon.minusBottom);
	dtree.icon.nlPlus=getExportedData(dtree.icon.nlPlus);
	dtree.icon.nlMinus=getExportedData(dtree.icon.nlMinus);
	dtree.icon.trash=getExportedData(dtree.icon.trash);
	dtree.icon.view=getExportedData(dtree.icon.view);
	dtree.icon.diff=getExportedData(dtree.icon.diff);
	dtree.icon.xml=getExportedData(dtree.icon.xml);
}

function refreshNextState()
{
	var STATES=6;

	if(g_RefreshingState > STATES)
		return;

	if(g_RefreshingState == 0)
	{
		document.getElementById("load_progress_parent").style.display="block";
	}

	document.getElementById("load_progress").style.width=(g_RefreshingState * 100 / STATES)+"%";
	g_RefreshingState++;

	switch(g_RefreshingState)
	{
	case 1:	refreshDatabases(); break;
	case 2: refreshDbRecords(); break;
	case 3: refreshVSConfig(); break;
	case 4: g_Started=1; refreshStatus(); break;
	case 5: logcat_view(); break;
	case 6: screenshot_list(); break;
	default: window.setTimeout(function(){ document.getElementById("load_progress_parent").style.display="none"; }, 1000);
	}
}

function start()
{
	if(g_Started != 2)
	{
		g_Started=2;
		document.getElementById('recordstree').innerHTML="";
		refreshStatus();
	}
}

function stop()
{
	g_Started=0;
}

function toggleRefresh()
{
	if(g_Started > 0)
	{
		stop();
	}
	else
	{
		start();
	}
}

function asyncHttpRequest(url, handler, postBody) 
{
	if (window.XMLHttpRequest) 
	{
		req = new XMLHttpRequest();
		req.onreadystatechange = handler;
		req.open(postBody ? "POST" : "GET", url, true);
		//if(postBody) req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		if(postBody) req.setRequestHeader("Content-length", postBody.length);
	  	req.send(postBody);
	}
	else if (window.ActiveXObject) 
	{
	  	req = new ActiveXObject("Microsoft.XMLHTTP");
	  	if (req)
	  	{
	  		req.onreadystatechange = handler;
	  		req.open(postBody ? "POST" : "GET", url, true);
			req.send(postBody);
	  	}
	}
}

function refreshStatus()
{
	if(g_Started == 0)
		return;

	if(g_WEB)
	{
		asyncHttpRequest(g_ProgressURL, notifyRequest, null);
	}
	else
	{
		refreshNextState();
	}
}

function refreshVSConfig()
{
	if(g_WEB)
	{
		asyncHttpRequest(g_VSConfigURL+'/get', notifyRequestVSConfig, null);
	}
	else
	{
		g_VSConfigTextData = getExportedData("listofsettings");
		redrawVSConfig(g_VSConfigTextData);
		refreshNextState();
	}
}

function notifyRequest()
{
	if ( req.readyState == 4)
	{
		if ( (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
		{
			var result = eval('({ "results": ' +req.responseText + '})');
			processProgress(result.results);
			refreshNextState();
		}

		if(g_Started == 2 && g_Timeout>0)
		{
			setTimeout('refreshStatus()', g_Timeout);
		}
		else
		{
			g_Started=0;
		}
	}
}

function notifyRequestVSConfig()
{
	if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
	{
		var data='({ "results": ' +req.responseText + '})';

		g_VSConfigTextData = eval(data).results;
		redrawVSConfig(g_VSConfigTextData);
		refreshNextState();
	}
}

function notifyVSConfigOnSave()
{
	if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
	{
		var result = eval('({ "results": ' +req.responseText + '})');
		if(result.results.code == 0)
		{
			alert("Settings applied.");
		}
		else
		{
			alert("Error applying settings.");
		}
		g_VSConfigTextData = eval(result).results;
		redrawVSConfig(g_VSConfigTextData);
	}
}


function notifyRequestDatabases()
{
	if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
	{
		var result = eval('({ "results": ' +req.responseText + '})');
		redrawDatabases(result.results);
		refreshNextState();
	}
}

function notifyRequestCmd()
{
	if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
	{
		var result = eval('({ "results": ' +req.responseText + '})');
		redrawDbRefresh(result.results);
		refreshNextState();
	}
}

function notifyRequestDiff()
{
	if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
	{
		var result = eval('({ "results": ' +req.responseText + '})');
		displayDiff(result.results);
	}
}

function notifyRequestContent()
{
	if( req.readyState == 4)
	{
		stopAnim();
		if((req.status == 200 || req.status == 304) && req.responseText.length > 0) 
		{
			var result = eval('({ "results": ' +req.responseText + '})');
			displayContent(result.results);
		}
	}
}

function redrawDbRefresh(results)
{
	if(! results || results.code != 0 || results.databases.length < 1)
		return;

	var index = 0;
	var oneIsRecorded = false;

	for(index=0; index<results.databases.length; index++)
	{
		var db=results.databases[index];
		var ctrl = document.getElementById('refresh'+db.name);

		if(ctrl)
		{
			ctrl.innerHTML=(db.isrecorded?"Stop":"Start")+ " "+ db.name;
			ctrl.className=db.isrecorded?"buttonon":"buttonoff";
			if(db.isrecorded)
			{
				oneIsRecorded = true;
			}
		}
	}	

	if(oneIsRecorded)
	{
		start();
	}
	else
	{
		stop();
	}
}

function redrawDatabases(results)
{
	if( ! results || results.code != 0 || results.databases.length < 1 )
		return;

	g_databasesTree = new dTree('g_databasesTree');
	if(!g_WEB)
	{
		loadDTreeImages(g_databasesTree);
	}

	var nlevel = 0;
	var index = 0;
	var lastStep = -1;

	g_databasesTree.add('base', -1, 'Databases on device:', '','','','app/img/base.gif');
	g_databasesTree.add('ourproducts', 'base', 'Products');
	g_databasesTree.add('android', 'base', 'Android providers');

	for(index=0; index<results.databases.length; index++)
	{
		var db=results.databases[index];
		var dbNodeId='dbs-'+db.name;

		if(db.isourproduct)
		{
			g_databasesTree.add(dbNodeId, db.isourproduct ? 'ourproducts':'android', db.name);
		}
		else
		{
			// dont add subfolder
			dbNodeId='android';
		}

		for(igroup=0; igroup<db.groups.length; igroup++)
		{
			var groupNodeId='group-'+index+'-'+igroup;
			var group=db.groups[igroup];

			g_databasesTree.add(groupNodeId, dbNodeId, group.name);

			for(itable=0; itable<group.tables.length; itable++)
			{
				if(!g_WEB && !getExportedData(group.tables[itable].uri))
				{
					continue;
				}

				var nodeId='tbl-'+index+'-'+itable;

				g_databasesTree.add(nodeId, groupNodeId, group.tables[itable].name);
				var node = g_databasesTree.aNodes[g_databasesTree.aNodes.length-1];
				node.userdata[0] = db.name;
				node.userdata[1] = group.tables[itable].uri;

				node.onview=function() { listTable(this,false);};
				node.onviewicon=null;
				if(g_WEB)
				{
					node.onviewXML=function() { listTable(this,true);};
				}
			}
		}
	}

	document.getElementById('databasestree').innerHTML=g_databasesTree;
	g_databasesTree.openTo(0,false,false);
}

function redrawVSConfig(results)
{
	if( ! results || results.code != 0 || results.products.length < 1 )
	{
		return;
	}

	var selector = document.getElementById('vsconfig_selector');
	var table = document.getElementById('vsconfig_table');
	if(!table)
	{
		return;
	}

	selector.options.length=0;
	while(table.rows.length>0)
	{
		table.deleteRow(0);
	}
	
	if(results.products.length == 0)
	{
		return;
	}

	var irow = 0;
	var iselected = 0;
	var product;

	for(var i=0; i<results.products.length; i++)
	{
		product = results.products[i];

		var option = document.createElement("option");
		option.value=product.pkg;
		option.text=product.pkg;

		selector.add(option);

		if(g_selectedPackage != null && g_selectedPackage == product.pkg)
		{
			iselected = i;
		}
	}

	selector.selectedIndex=iselected;

	product = results.products[iselected];
	if(! product.config)
	{
		return;
	}
	
	for(var c=0; c < product.config.length; c++)
	{
		var config = product.config[c];

		var row = table.insertRow(irow++);

		var cellname=row.insertCell(0);
		var cellvalue=row.insertCell(1);

		cellname.className="vsconfigname";
		cellvalue.className="vsconfigvalue";

		cellname.appendChild(document.createTextNode(config.name));

		if(config.type == "boolean")
		{
			var configSelector = document.createElement("select");
			cellvalue.appendChild(configSelector);

			var optionTrue = document.createElement("option");
			optionTrue.value="true";
			optionTrue.text="true";

			var optionFalse = document.createElement("option");
			optionFalse.value="false";
			optionFalse.text="false";

			configSelector.add(optionTrue);
			configSelector.add(optionFalse);

			configSelector.selectedIndex=config.value=="true" ? 0 : 1;
		}
		else
		{
			var input = document.createElement("input");
			input.value=config.value;
			input.type="TEXT";
			cellvalue.appendChild(input);					
		}
	}
}

function processProgress(results)
{
	if( ! results || results.code != 0 || results.databases.length < 1 )
		return;

	g_recordsTree = new dTree('g_recordsTree');
	if(!g_WEB)
	{
		loadDTreeImages(g_recordsTree);
	}

	var nlevel = 0;
	var index = 0;
	var lastStep = -1;

	g_recordsTree.add('base', -1, 'Changes on device:', '','','','app/img/base.gif');

	for(index=0; index<results.databases.length; index++)
	{
		var db=results.databases[index];
		var dbNodeId='db-'+db.name;

		g_recordsTree.add(dbNodeId, 'base', db.name);

		for(irec=0; irec<db.records.length; irec++)
		{
			var nodeId='db-'+index+'-'+irec;

			g_recordsTree.add(nodeId, dbNodeId, db.records[irec].date);
			var node = g_recordsTree.aNodes[g_recordsTree.aNodes.length-1];
			node.userdata[0] = db.name;
			node.userdata[1] = db.records[irec].name;

			node.ondelete=function() { onDeleteNode(this); };
			node.onview=function() { onViewNode(this);};
			node.onselect=function(selected) { onSelectNode(this, selected); };
			node.ondiff=function() { onViewDiff(this); };

			node.onviewicon=g_recordsTree.icon.view;
		}
	}

	document.getElementById('recordstree').innerHTML=g_recordsTree;
	//g_recordsTree.closeAll();
	g_recordsTree.openTo(g_currentOpenedDb,false,false);
}

function onDeleteNode(node)
{
	asyncHttpRequest('/tools/record/delete/'+node.userdata[0]+'/'+node.userdata[1], notifyRequest, null);
	g_currentOpenedDb=node.id;
}

function onViewNode(node)
{
	oDiff=document.getElementById('diffrecord');
	asyncHttpRequest('/tools/record/content/'+node.userdata[0]+'/'+node.userdata[1], notifyRequestContent, null);
}

function onViewDiff(node)
{
	if(g_selectedRecords.length != 2)
		return;

	var node1= g_recordsTree.aNodes[g_selectedRecords[0]];
	var node2= g_recordsTree.aNodes[g_selectedRecords[1]];

	
	var url="/tools/record/diff/"+node1.userdata[0]+"/"+node1.userdata[1]+"/"+node2.userdata[1];
	requestDiff(url);
}

function onSelectNode(node, selected)
{
	if(!selected)
	{
		if(g_selectedRecords.length == 0)
		{
			return;
		}
		
		if(g_selectedRecords[0] == node._ai)
		{
			document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[0]).checked=false;
			if(g_selectedRecords.length == 2)
			{
				g_selectedRecords[0] = g_selectedRecords[1];
				g_selectedRecords.length = 1;
			}
			else
			{
				g_selectedRecords.length = 0;
			}
		}
		else if(g_selectedRecords.length == 2 && g_selectedRecords[1] == node._ai)
		{
			document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[1]).checked=false;
			g_selectedRecords.length = 1;
		}
	}
	else
	{
		if(g_selectedRecords.length == 0)
		{
			g_selectedRecords[0] = node._ai;
		}
		else if(g_selectedRecords.length == 1)
		{
			if(g_selectedRecords[0] != node._ai)
			{
				if(g_recordsTree.aNodes[g_selectedRecords[0]].userdata[0] == node.userdata[0])
				{
					g_selectedRecords[1] = node._ai;
				}
				else
				{
					document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[0]).checked=false;
					g_selectedRecords[0] = node._ai;
				}
			}
		}
		else
		{
			if(g_selectedRecords[0] != node._ai && g_selectedRecords[1] != node._ai)
			{
				if(g_recordsTree.aNodes[g_selectedRecords[0]].userdata[0] == node.userdata[0])
				{
					document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[0]).checked=false;
					g_selectedRecords[0] = g_selectedRecords[1];
					g_selectedRecords[1] = node._ai;
				}
				else
				{
					document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[0]).checked=false;
					document.getElementById('cb'+g_recordsTree.obj+g_selectedRecords[1]).checked=false;
					g_selectedRecords.length = 1;
					g_selectedRecords[0] = node._ai;	
				}
			}
		}
	}

	for(var i=0; i<g_recordsTree.aNodes.length; i++)
	{
		if(g_recordsTree.aNodes[i].ondiff)
		{
			if(g_selectedRecords.length < 2)
			{
				document.getElementById('di' + g_recordsTree.obj + i).style.display = 'none';
			}
			else
			{
				document.getElementById('di' + g_recordsTree.obj + i).style.display = i==g_selectedRecords[1] ? null : 'none';
			}
		}
	}
}

function refreshDatabases()
{
	if(g_WEB)
	{
		asyncHttpRequest(g_databasesURL+"/list", notifyRequestDatabases, null);
	}
	else
	{
		redrawDatabases(getExportedData("listofdatabases"));
		refreshNextState();
	}
}

function refreshDbRecords()
{
	if(g_WEB)
	{
		asyncHttpRequest(g_recordURL+"/infos", notifyRequestCmd, null);
	}		
	else
	{
		refreshNextState();
	}
}

function toggleDbRecord(db)
{
	if(g_WEB)
	{
		asyncHttpRequest(g_recordURL+"/toggle/"+db, notifyRequestCmd, null);
	}
}

function requestDiff(url)
{
	asyncHttpRequest(url, notifyRequestDiff, null);		
}

function displayDiff(results)
{
	var tbody = "";

	if(results.changes.length == 0)
	{
		tbody = "<h3>no changes.</h3>";
	}
	else
	{
		var index = 0;
		var colindex = 0;

		tbody = '<table border="1">\n';

		if(results.cols)
		{
			tbody += '<tr>';
			
			tbody += '<th></th>';
			for(icol=0; icol<results.cols.length; icol++)
			{
				tbody += '<th>'+ results.cols[icol].name + '</th>';
			}
			
			tbody += '</tr>\n';
		}

		for(index=0; index<results.changes.length; index++)
		{
			var change=results.changes[index];
			
			tbody += '<tr class="' + change.change + '">';
			
			tbody += '<td class="' + change.change + '">'+ change.change + '</td>';
			
			for(colindex=0; colindex<change.cols.length; colindex++)
			{
				var col=change.cols[colindex];
				if(change.change == "UPDATED")
				{
					tbody += '<td class="' + col.change + '">';
				}
				else
				{
					tbody += '<td>';
				}
				tbody += col.value + '</td>';
			}
			
			tbody += '</tr>\n';
		}

		tbody += '</table>';
	}

	document.getElementById('diffrecord').innerHTML=tbody;
}

function displayContent(results)
{
	if(!oDiff || results == null)
		return;

	if(results.msg)
	{
		oDiff.innerHTML=results.msg;
		return;
	}

	var tbody = '<br/><br/><h2>'+ results.name+ ' (count: '+ results.content.length+')</h2><table border="1">\n';
	var index = 0;
	var colindex = 0;

	if(results.content.length > 0)
	{
		if(results.cols)
		{
			tbody += '<tr>';
			
			for(icol=0; icol<results.cols.length; icol++)
			{
				tbody += '<th>'+ results.cols[icol].name + '</th>';
			}
			
			tbody += '</tr>\n';
		}

		for(index=0; index<results.content.length; index++)
		{
			var content=results.content[index];
			
			tbody += '<tr>';
			
			for(colindex=0; colindex<content.cols.length; colindex++)
			{
				var col=content.cols[colindex];
				tbody += '<td>' + col.value + '</td>';
			}
			
			tbody += '</tr>\n';
		}
	}

	tbody += '</table>';
	oDiff.innerHTML=tbody;
}

function listTable(table,xml)
{
	oDiff=document.getElementById('dbcontent');
	var uri = table.userdata[1];

	if(xml)
	{
		document.location.href=g_databasesURL+"/listtable?f=xml&uri="+encodeURIComponent(uri);
		return;
	}

	document.getElementById('querytree').style.display='none';
	document.getElementById('query_uri').value=uri;

	redrawQueryForm();
	

//	startAnim();
//	var body=" { \"uri\":\""+table.userdata[1]+"\" } ";
//	asyncHttpRequest(g_databasesURL+'/listtable', notifyRequestContent, body);	
}

function redrawQueryForm()
{
	var uri = document.getElementById('query_uri').value;

	if(g_WEB)
	{
		var bIsInstance = uri == "content://com.android.calendar/instances/when";

		document.getElementById('query_tr_selection').style.display = (!bIsInstance) ? "table-row" : "none";
		document.getElementById('query_tr_sort').style.display = (!bIsInstance) ? "table-row" : "none";
		document.getElementById('query_tr_begin').style.display = (bIsInstance) ? "table-row" : "none";
		document.getElementById('query_tr_end').style.display = (bIsInstance) ? "table-row" : "none";
	}
	else
	{

		document.getElementById('query_tr_projection').style.display = "none";
		document.getElementById('query_tr_selection').style.display = "none";
		document.getElementById('query_tr_sort').style.display = "none";
		document.getElementById('query_tr_begin').style.display = "none";
		document.getElementById('query_tr_end').style.display = "none";

		document.getElementById('query_execute').style.display = "none";
		document.getElementById('query_delete').style.display = "none";

		document.getElementById('query_delete').style.display = "none";
		displayContent(getExportedData(uri));
		return;
	}
}

function startAnim()
{
//alert("start");
	document.body.style.cursor = 'wait';
}

function stopAnim()
{
//alert("stop");
	document.body.style.cursor = 'auto';
}

function vsconfig_OnChange(selectedPkg)
{
	g_selectedPackage=selectedPkg;

	redrawVSConfig(g_VSConfigTextData);	
}

function vsconfig_onSave(selectedPkg)
{
	var table = document.getElementById('vsconfig_table');
	if(!table || table.rows.length == 0)
	{
		return;
	}

	var body = "{ \"pkg\": \""+selectedPkg+"\", \"config\": {";

	for(var iRow=0; iRow<table.rows.length; iRow++)
	{
		var row = table.rows[iRow];
		
		body += "\"" + row.children[0].textContent + "\":\"" + row.children[1].firstChild.value + "\", ";
	}

	body += " \"_code\":0 } } ";

	asyncHttpRequest(g_VSConfigURL+'/set', notifyVSConfigOnSave, body);	
}

function getExportedData(uri)
{ 
	for(var i=0; i<g_ExportedData.length; i++) 
	{
		if(g_ExportedData[i].uri == uri)
		{
			return g_ExportedData[i].results;
		}
	}

	return null;
}


function addExportedData(data)
{
	g_ExportedData[g_ExportedData.length] = data;
}

function onExport(ok)
{
	document.getElementById('export_popup').style.display="none";
	if(ok)
	{
		document.location.href="export";
	}
}

function logcat_refresh(clear,reload)
{
	function notifyRequestLogcat()
	{
		if(req.readyState == 4) 
		{
			stopAnim();
			if((req.status == 200 || req.status == 304) && req.responseText.length > 0)
			{
				document.getElementById('logcat_size').innerHTML = "size = "+ bytesToSize(req.responseText.length);
				redrawLogcat(req.responseText);
			}

			refreshNextState();
		}
	}


	if(g_WEB)
	{
		var body=" { \"clear\": "+ clear+ ", \"reload\": "+reload+" } ";

		startAnim();
		asyncHttpRequest(g_LogcatURL, notifyRequestLogcat, body);
	}
	else
	{
		document.getElementById('logcat').innerHTML=logcatColor(htmlDecode(getExportedData("logcat")));
		refreshNextState();
	}
}

function redrawLogcat(results)
{
	stopAnim();
	document.getElementById('logcat').innerHTML=logcatColor(htmlDecode(results)); //.replace(/\n\r?/g, '<br />');
}

function logcat_toggleRecord()
{
	logcat_refreshRecording(true,false);
}

function logcat_clear()
{
	logcat_refresh(true,false);
}

function logcat_view()
{
	logcat_refresh(false,false);
}

function bytesToSize(bytes) 
{
	var sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
	if (bytes == 0) return '0 Bytes';
	if (bytes == 1) return '1 Byte';
	var i = parseInt(Math.floor(Math.log(bytes) / Math.log(1024)));
	return Math.round(bytes / Math.pow(1024, i), 2) + ' ' + sizes[i];
}

function logcat_refreshRecording(toggle,clear)
{
	function notifyToggleRec()
	{
		if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
		{
			var result = eval('({ "results": ' +req.responseText + '})');
			if(result.results.code == 0)
			{
				var ctrl = document.getElementById('logcat_record');
				var size = result.results.size;
				ctrl.innerHTML= result.results.recording ? "Stop recording ("+bytesToSize(size)+")" : "Start recording";
				ctrl.className=result.results.recording ?"buttonon":"buttonoff";
				if(result.results.recording)
				{
					setTimeout('logcat_refreshRecording(false,false)', g_Timeout);
				}
				else
				{
					setTimeout('logcat_view()', 200);
				}
			}
		}
	}
	
	if(g_WEB)
	{
		var url = g_LogcatURL + ( (toggle) ? '/togglerec' : '/refreshrec');
		asyncHttpRequest(url, notifyToggleRec, null);
	}
}

function downloadLogcat()
{
	if(g_WEB)
	{
		document.location.href=g_LogcatURL+'/export';
	}
	else
	{
		document.body.innerHTML=logcatColor(htmlDecode(getExportedData("logcat")));
	}
}

function htmlEncode( input ) 
{
    return String(input)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '\\\'')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;');
}

function logcatColor(input)
{
	function replacer(match, p1, p2, p3, offset, string)
	{
		return '<p class="lc' + p2 + '">' + match + '</p>';
	};

	return String(input).replace(/([0-9]*-[0-9]* *[^ ]* *[^ ]* *[^ ]*) ([VDIWE]) (.*)/g, replacer);
}

function htmlDecode( input ) 
{
    return String(input)
		.replace(/\</g, '&lt;')
		.replace(/\>/g, '&gt;')
        .replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
        .replace(/\r/g, '')
        .replace(/\n/g, '<br />\n')
	;
}

function query_execute(really)
{
	function notifyRequestQuery()
	{
		if ( req.readyState == 4 && (req.status == 200 || req.status == 304) && req.responseText.length > 0) 
		{
			stopAnim();
			oDiff=document.getElementById('dbcontent');
			var result = eval('(' +req.responseText + ')');
			displayContent(result);

			if(result.code == 0 && result.content && result.content.length > 0)
			{
				document.getElementById('query_delete').style.display= "inline";
				document.getElementById('query_delete').innerHTML="Delete " + (result.content.length==1 ? ("this row?") : ("these "+ result.content.length +" rows"));
			}
			else
			{
				document.getElementById('query_delete').style.display= "none";
			}
		}
	}

	if(g_WEB)
	{
		var uri = document.getElementById('query_uri').value;
		if(!uri)
		{
			alert("select first a table.");
			return;
		}

		var body=" { \"uri\": \""+ uri + "\", \"command\":\""+ (really ? "delete" : "query") + "\""; 

		body += ", \"projection\": [ " ;
		if(document.getElementById('query_projection').value.trim().length > 0)
		{
			var proj = document.getElementById('query_projection').value.trim().split(',');
			for (var n=0; n<proj.length; n++)
			{
				body += (n==0?"":",") + "\""+proj[n].trim()+"\"" ;
			}
		}
		body += " ] ";

		if(document.getElementById('query_sort').value.length > 0)
		{
			body += ", \"sort\":\"" + document.getElementById('query_sort').value.trim() + "\"";
		}

		if(document.getElementById('query_tr_begin').style.display != "none")
		{
			body += ", \"begin\":"+ document.getElementById('query_begin').value.trim() + ", \"end\":"+ document.getElementById('query_end').value.trim();
		}
		else
		{
			var selection = document.getElementById('query_selection').value.replace(/"/g, '\\"').replace(/'/g, '\\\'');
			body += ", \"selection\":\""+ selection + "\"";
		}

		body += " } ";

		startAnim();
		asyncHttpRequest(g_QueryURL, notifyRequestQuery, body);
	}
}

function datetools_dateToMilli(mydate)
{
	myformat='dd-mm-yyyy HH:MM:ss';
	
	dtsplit=mydate.split(/[- :]/);
	dfsplit=myformat.split(/[- :]/);
	
	// creates assoc array for date
	df = new Array();
	for(dc=0;dc<6;dc++)
	{
        df[dfsplit[dc]]=dtsplit[dc];
    }
	
	// uses assc array for standard mysql format
	dd = new Date(df['yyyy'], df['mm']-1, df['dd'], df['HH'], df['MM'], df['ss'], 0);
//alert("milli: "+ mydate + " --> "+dd.getTime());
	return dd.getTime();
}

function datetools_milliToDate(mydate)
{
	var newd = new Date();
	newd.setTime(mydate);
//alert("date: "+ newd.toString());
	return newd.getDate() + '-' + (newd.getMonth()+1) + '-'+ newd.getFullYear() + ' '+ newd.getHours()+':'+ newd.getMinutes()+ ':'+ newd.getSeconds();
}


function datetools_convert(obj)
{
	var obj1 = document.getElementById('query_datetimemilli');
	var obj2 = document.getElementById('query_datetimeentry');

	var func;
	if(obj == obj1)
	{
		func = obj2.onchange;
		obj2.onchange = null;
		obj2.value = datetools_milliToDate(obj1.value);
		obj2 = func;
	}
	else
	{
		func = obj1.onchange;
		obj1.onchange = null;
		obj1.value = datetools_dateToMilli(obj2.value);
		obj1 = func;
	}
}

function screenshot_list()
{
	function notifyScreenshotList()
	{
		if ( req.readyState == 4) 
		{
			if((req.status == 200 || req.status == 304) && req.responseText.length > 0)
			{
				var result = eval('(' +req.responseText + ')');
				redrawScreenshots(result);
			}
			refreshNextState();
		}
	}


	if(g_WEB)
	{
		asyncHttpRequest(g_ScreenshotsListURL, notifyScreenshotList, null);
	}
	else
	{
		document.getElementById("screenshot_btn").style.display="none";
		var list = getExportedData("listofscreenshots");
		if(list)
		{
			redrawScreenshots(list);
		}
		refreshNextState();
	}
}

function redrawScreenshots(results)
{
	if(!results || !results.list)
	{
		return;
	}

	var box = document.getElementById("screenshots_box");
	while(box.firstChild)
	{
		box.removeChild(box.firstChild);
	}
	box.style.display="none";
	document.getElementById("screenshots_textdrag").style.display="block";
	document.getElementById("screenshots_expunge").style.display="none";

	var container = document.getElementById("screenshots");
	while(container.firstChild)
	{
		container.removeChild(container.firstChild);
	}

	for(var i=0; i<results.list.length; i++)
	{
		img = new Image();
		img.id="screenshot"+i;
		img.label=results.list[i].name;
		img.src = getImageData(g_ScreenshotURL+"?name="+results.list[i].name);
		img.className="screenshotMin";
		img.onclick=function(){screenshot_onclick(this)};
//		img.onmouseover=function(){screenshot_onmouseover(this)};
//		img.onmouseout=function(){screenshot_onmouseout(this)};
		img.ondragstart=drag;

		container.appendChild(img);
	}

	if(container.children.length == 0)
	{
		document.getElementById("boxdelete").style.display = "none";		
		document.getElementById("screenshots").style.display = "none";		
		document.getElementById("clicktodragtext").style.display = "none";
	}
	else
	{
		document.getElementById("boxdelete").style.display = "inline-block";
		document.getElementById("screenshots").style.display = "block";		
		document.getElementById("clicktodragtext").style.display = "block";
	}
}

function screen_shot()
{
	function notifyNewScreenshot()
	{
		if ( req.readyState == 4 && (req.status == 200 || req.status == 304)) 
		{
			stopAnim();
			if(req.responseText.length > 0)
			{
				var result = eval('(' +req.responseText + ')');
				redrawScreenshots(result);
			}
			else
			{
				alert("Error creating new snapshot: is adbd started on device?");
			}
		}
	}
	
	if(g_WEB)
	{
		startAnim();	
		asyncHttpRequest(g_ScreenshotURL, notifyNewScreenshot, null);
	}
}

function openInNewTab(url )
{
	var win=window.open(url, '_blank');
	win.focus();
}

function screenshot_onclick(obj)
{
	openInNewTab(obj.src);
	//obj.className = obj.className=="screenshotMax" ? "screenshotMin" : "screenshotMax";
}

function screenshot_onmouseover(obj)
{
	document.getElementById(obj.id+"_trash").style.display="block";
}

function screenshot_onmouseout(obj)
{
	document.getElementById(obj.id+"_trash").style.display="none";
}

function screenshots_expunge()
{
	function notifyScreenshotDelete()
	{
		if ( req.readyState == 4) 
		{
			if((req.status == 200 || req.status == 304) && req.responseText.length > 0)
			{
				var result = eval('(' +req.responseText + ')');
				redrawScreenshots(result);
			}
		}
	}


	if(g_WEB)
	{
		var box = document.getElementById("screenshots_box");
		if(box.children.length > 0)
		{
			var body = "{ \"list\": [";
			var bFirst = true;
			for(var i=0; i<box.children.length; i++)
			{	
				body += (bFirst?"":",") +"\"" + box.children[i].label + "\" ";
				bFirst = false;
			}

			body += " ] } ";
			asyncHttpRequest(g_ScreenshotURL+"/delete", notifyScreenshotDelete, body);
		}
	}
}

function drag(evt) 
{
	evt.dataTransfer.setData("Text", evt.target.id);
}

function drop(evt) 
{
	evt.preventDefault();

	var id = evt.dataTransfer.getData("Text");
	var container;
	var box = document.getElementById("screenshots_box");

	if(evt.target.id == "boxdelete" || evt.target.id == "screenshots_box" || evt.target.id == "screenshots_textdrag" || evt.target.className=="imgtrash")
	{
		container = box;
		document.getElementById(id).className="imgtrash";

		box.style.display="inline-block";
		document.getElementById("screenshots_textdrag").style.display="none";
		document.getElementById("screenshots_expunge").style.display="inline";
	}
	else
	{
		container = document.getElementById("screenshots");
		document.getElementById(id).className="screenshotMin";
	}


	container.appendChild(document.getElementById(id));

	if(box.children.length == 0)
	{
		document.getElementById("screenshots_textdrag").style.display="block";
		document.getElementById("screenshots_expunge").style.display="none";
		box.style.display="none";
	}
	else
	{
		document.getElementById("screenshots_expunge").innerHTML="Delete "+ box.children.length + " screenshot"+ (box.children.length == 1?"":"s");
	}
}
