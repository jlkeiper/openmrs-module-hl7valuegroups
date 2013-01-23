<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<%@ include file="template/localHeader.jsp"%>

<p>Use the form below to pick a file to submit to the HL7 queue.</p>

<form method="post" enctype="multipart/form-data">
    <input type="file" name="hl7" />
    <input type="submit"/>
</form>

<%@ include file="/WEB-INF/template/footer.jsp"%>