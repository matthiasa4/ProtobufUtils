package com.datatonic.sky.sky_q_poc.protobuffer.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.common.base.CaseFormat;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

public class ProtobufUtils {

	public static String getFieldType(String in) {
		String className = "";
		if (in.toLowerCase().equals("boolean")) {
			className = "BOOLEAN";
		} else if (in.toLowerCase().contains(".string")) {
			className = "STRING";
		} else if (in.toLowerCase().equals("int") || in.toLowerCase().equals("int32")
				|| in.toLowerCase().equals("int64")) {
			className = "INTEGER";
		} else if (in.toLowerCase().equals("float") || in.toLowerCase().equals("double")) {
			className = "FLOAT";
		}
		return className;
	}

	public static TableSchema makeTableSchema(Descriptor d) {
		TableSchema res = new TableSchema();

		List<FieldDescriptor> fields = d.getFields();

		List<TableFieldSchema> schema_fields = new ArrayList<TableFieldSchema>();

		for (FieldDescriptor f : fields) {
			String type = "STRING";
			String mode = "NULLABLE";

			if (f.isRepeated()) {
				mode = "REPEATED";
			}

			if (f.getType().toString().toUpperCase().contains("BYTES")) {
				type = "BYTES";
			} else if (f.getType().toString().toUpperCase().contains("INT")) {
				type = "INTEGER";
			} else if (f.getType().toString().toUpperCase().contains("BOOL")) {
				type = "BOOLEAN";
			} else if (f.getType().toString().toUpperCase().contains("FLOAT")
					|| f.getType().toString().toUpperCase().contains("DOUBLE")) {
				type = "FLOAT";
			} else if (f.getType().toString().toUpperCase().contains("MESSAGE")) {
				type = "RECORD";
				TableSchema ts = makeTableSchema(f.getMessageType());

				schema_fields.add(new TableFieldSchema().setName(f.getName().replace(".", "_")).setType(type)
						.setMode(mode).setFields(ts.getFields()));
			}

			if (!type.equals("RECORD")) {
				schema_fields
						.add(new TableFieldSchema().setName(f.getName().replace(".", "_")).setType(type).setMode(mode));
			}
		}

		res.setFields(schema_fields);

		return res;
	}

	public static TableRow makeTableRow(Message message) {
		TableRow res = new TableRow();
		List<FieldDescriptor> fields = message.getDescriptorForType().getFields();

		for (FieldDescriptor f : fields) {
			String type = "STRING";
			if (f.isRepeated() || (!f.isRepeated() && message.hasField(f))) {
				if (f.getType().toString().toUpperCase().contains("STRING")) {
					res.set(f.getName().replace(".", "_"), String.valueOf(message.getField(f)));
				} else if (f.getType().toString().toUpperCase().contains("BYTES")) {
					res.set(f.getName().replace(".", "_"), (byte[]) message.getField(f));
				} else if (f.getType().toString().toUpperCase().contains("INT32")) {
					res.set(f.getName().replace(".", "_"), (int) message.getField(f));
				} else if (f.getType().toString().toUpperCase().contains("INT64")) {
					res.set(f.getName().replace(".", "_"), (long) message.getField(f));
				} else if (f.getType().toString().toUpperCase().contains("BOOL")) {
					res.set(f.getName().replace(".", "_"), (boolean) message.getField(f));
				} else if (f.getType().toString().toUpperCase().contains("FLOAT")
						|| f.getType().toString().toUpperCase().contains("DOUBLE")) {
					res.set(f.getName().replace(".", "_"), (double) message.getField(f));
				} else if (f.getType().toString().toUpperCase().contains("MESSAGE")) {
					type = "RECORD";
					if (message.getAllFields().containsKey(f)) {
						if (f.isRepeated()) {
							List<TableRow> tr = ((List<Message>) message.getField(f)).stream().map(m -> makeTableRow(m))
									.collect(Collectors.toList());
							res.set(f.getName().replace(".", "_"), tr);
						} else {
							TableRow tr = makeTableRow((Message) message.getField(f));
							res.set(f.getName().replace(".", "_"), tr);
						}
					}
				}
			}
		}

		return res;
	}

	// TODO: check repeated vs nested
	// TODO: check naming conventions
	public static Message makeMessage(TableRow tablerow, Message.Builder message) {
		TableRow res = new TableRow();
		List<FieldDescriptor> fields = message.getDescriptorForType().getFields();

		for (FieldDescriptor f : fields) {
			Object currentField = tablerow.get(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, f.getName()));
			if (currentField != null) {
				if (f.getType().toString().toUpperCase().contains("STRING")) {
					message.setField(f, String.valueOf(currentField));
				} else if (f.getType().toString().toUpperCase().contains("BYTES")) {
					message.setField(f, (String.valueOf(currentField)).getBytes());
				} else if (f.getType().toString().toUpperCase().contains("INT32")) {
					message.setField(f, Integer.parseInt(String.valueOf(currentField)));
				} else if (f.getType().toString().toUpperCase().contains("INT64")) {
					message.setField(f, Long.parseLong(String.valueOf(currentField)));
				} else if (f.getType().toString().toUpperCase().contains("BOOL")) {
					message.setField(f, Boolean.parseBoolean(String.valueOf(currentField)));
				} else if (f.getType().toString().toUpperCase().contains("FLOAT")
						|| f.getType().toString().toUpperCase().contains("DOUBLE")) {
					message.setField(f, Double.parseDouble(String.valueOf(currentField)));
				} else if (f.getType().toString().toUpperCase().contains("MESSAGE")) {
					if (f.isRepeated()) {
						if (tablerow.get(f.getName()) != null && ((List<?>) currentField).size() > 0) {
							List<Message> m = new ArrayList<Message>();
							for (Object o : (List<Object>) currentField) {
								if (o.getClass() == TableRow.class) {
									m.add(makeMessage((TableRow) o, message.newBuilderForField(f)));
								} else {
									m.add(makeMessage((parseLinkedHashMapToTableRow((LinkedHashMap<String, Object>) o)),
											message.newBuilderForField(f)));
								}

							}
							message.setField(f, m);
						}
					} else if (tablerow
							.get(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, f.getName())) != null) {
						if (currentField.getClass() != TableRow.class) {
							message.setField(f,
									makeMessage(
											parseLinkedHashMapToTableRow((LinkedHashMap<String, Object>) currentField),
											message.newBuilderForField(f)));

						} else {
							message.setField(f, makeMessage((TableRow) currentField, message.newBuilderForField(f)));
						}
					}
				}
			}
		}
		return message.build();
	}

	public static TableRow parseLinkedHashMapToTableRow(LinkedHashMap<String, Object> input) {
		TableRow out = new TableRow();
		for (Entry<String, Object> e : input.entrySet()) {
			if (e.getValue().getClass() != LinkedHashMap.class && e.getValue().getClass() != TableRow.class) {
				out.set(e.getKey(), e.getValue());
			} else {
				out.set(e.getKey(), parseLinkedHashMapToTableRow((LinkedHashMap<String, Object>) e.getValue()));
			}
		}
		return out;
	}
}
