#!/bin/sh
# ==============================================================================
# MediTriaje 2.0 — Script de Preparación de Oracle Wallet para Render / Docker
# ==============================================================================
# Este script decodifica la wallet de Oracle ATP suministrada en Base64
# (vía variable de entorno ORACLE_WALLET_BASE64 o WALLET_BASE64) o verifica
# los Secret Files montados en /etc/secrets/wallet.
# NUNCA imprime secretos, contraseñas ni el contenido de la wallet.
# ==============================================================================

set -e

# Directorio destino por defecto para la wallet
WALLET_TARGET_DIR="${TNS_ADMIN:-/etc/secrets/wallet}"

# Si /etc/secrets/wallet no es escribible (p. ej. en contenedor sin permisos root), usar /app/wallet o /tmp/oracle-wallet
if [ ! -d "$WALLET_TARGET_DIR" ]; then
    mkdir -p "$WALLET_TARGET_DIR" 2>/dev/null || WALLET_TARGET_DIR="/tmp/oracle-wallet"
    mkdir -p "$WALLET_TARGET_DIR"
elif [ ! -w "$WALLET_TARGET_DIR" ]; then
    WALLET_TARGET_DIR="/tmp/oracle-wallet"
    mkdir -p "$WALLET_TARGET_DIR"
fi

WALLET_B64="${ORACLE_WALLET_BASE64:-$WALLET_BASE64}"

if [ -n "$WALLET_B64" ]; then
    echo "[prepare-wallet] Detectada variable de entorno de Wallet en Base64. Decodificando en $WALLET_TARGET_DIR..."
    
    TMP_ZIP="/tmp/wallet_temp_$$.zip"
    
    # Decodificar Base64 tolerando variantes de base64 en alpine / debian
    if echo "$WALLET_B64" | base64 -d > "$TMP_ZIP" 2>/dev/null; then
        :
    elif echo "$WALLET_B64" | base64 --decode > "$TMP_ZIP" 2>/dev/null; then
        :
    elif echo "$WALLET_B64" | openssl base64 -d -A > "$TMP_ZIP" 2>/dev/null; then
        :
    else
        echo "[prepare-wallet] ERROR: No fue posible decodificar la cadena Base64 de la wallet." >&2
        rm -f "$TMP_ZIP"
        exit 1
    fi

    # Descomprimir archivos de la wallet
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$TMP_ZIP" -d "$WALLET_TARGET_DIR"
    elif command -v jar >/dev/null 2>&1; then
        (cd "$WALLET_TARGET_DIR" && jar -xf "$TMP_ZIP")
    else
        echo "[prepare-wallet] ERROR: Ni 'unzip' ni 'jar' estan disponibles para descomprimir la wallet." >&2
        rm -f "$TMP_ZIP"
        exit 1
    fi
    
    rm -f "$TMP_ZIP"
    
    # Restringir permisos a solo lectura del propietario
    chmod 700 "$WALLET_TARGET_DIR" 2>/dev/null || true
    chmod 600 "$WALLET_TARGET_DIR"/* 2>/dev/null || true
    
    echo "[prepare-wallet] Wallet decodificada y configurada exitosamente."
else
    # Verificar si ya existen archivos montados como Secret Files
    if [ -f "$WALLET_TARGET_DIR/cwallet.sso" ] || [ -f "/etc/secrets/wallet/cwallet.sso" ]; then
        if [ -f "/etc/secrets/wallet/cwallet.sso" ] && [ "$WALLET_TARGET_DIR" != "/etc/secrets/wallet" ]; then
            cp -r /etc/secrets/wallet/* "$WALLET_TARGET_DIR/" 2>/dev/null || true
        fi
        echo "[prepare-wallet] Detectados archivos de wallet montados en $WALLET_TARGET_DIR."
    else
        echo "[prepare-wallet] NOTA: No se suministro wallet en Base64 ni se detectaron archivos montados en $WALLET_TARGET_DIR."
        echo "[prepare-wallet] Si la conexion a Oracle ATP es TLS directa (puerto 1522 sin wallet), este paso es opcional."
    fi
fi

# Ajustar sqlnet.ora si existe para que apunte al directorio efectivo de TNS_ADMIN
if [ -f "$WALLET_TARGET_DIR/sqlnet.ora" ]; then
    # Reemplazar DIRECTORY="?/network/admin" por el path absoluto efectivo
    sed -i "s|DIRECTORY=\"?/network/admin\"|DIRECTORY=\"$WALLET_TARGET_DIR\"|g" "$WALLET_TARGET_DIR/sqlnet.ora" 2>/dev/null || true
fi

# Exportar variable de entorno si el script es ejecutado con source / .
export TNS_ADMIN="$WALLET_TARGET_DIR"
echo "[prepare-wallet] TNS_ADMIN configurado en: $TNS_ADMIN"
exit 0
