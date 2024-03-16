#!/bin/bash

content_field="$1"
original_dialect="$2"
target_dialect="$3"

if [[ -z $target_dialect ]]
then
  target_dialect="SQLSERVER"
fi

if [[ -z "$original_dialect" ]]
then
  original_dialect="POSTGRES"
fi

json_string=$(echo '{}' | jq --arg content "${content_field}" '.messages = [.role = "user" | .content = $content] | .model = "gpt-3.5-turbo"')

openai_output=$(curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d "$json_string" | jq -r '.choices[0].message.content')

cat <<< "$openai_output"
