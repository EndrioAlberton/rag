<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title>Settings</v-card-title>
          <v-card-text>
            <v-list>
              <v-list-item to="/settings/2fa" prepend-icon="mdi-shield-lock">
                <v-list-item-title>Two-Factor Authentication</v-list-item-title>
                <v-list-item-subtitle>Configure additional security for your account</v-list-item-subtitle>
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
export default {
  name: 'Settings'
};
</script>

