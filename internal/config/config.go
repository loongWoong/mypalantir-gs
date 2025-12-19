package config

import (
	"log"
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

// Config 应用配置
type Config struct {
	Server ServerConfig
	DSL    DSLConfig
	Data   DataConfig
	Log    LogConfig
	Frontend FrontendConfig
}

// ServerConfig 服务器配置
type ServerConfig struct {
	Port string
	Mode string
}

// DSLConfig DSL 配置
type DSLConfig struct {
	FilePath string
}

// DataConfig 数据存储配置
type DataConfig struct {
	RootPath string
}

// LogConfig 日志配置
type LogConfig struct {
	Level string
	File  string
}

// FrontendConfig 前端配置
type FrontendConfig struct {
	StaticPath string
}

var globalConfig *Config

// Load 加载配置
func Load() (*Config, error) {
	// 尝试加载 .env 文件，如果不存在也不报错
	_ = godotenv.Load()

	config := &Config{
		Server: ServerConfig{
			Port: getEnv("SERVER_PORT", "8080"),
			Mode: getEnv("SERVER_MODE", "debug"),
		},
		DSL: DSLConfig{
			FilePath: getEnv("DSL_FILE_PATH", "./ontology/schema.yaml"),
		},
		Data: DataConfig{
			RootPath: getEnv("DATA_ROOT_PATH", "./data"),
		},
		Log: LogConfig{
			Level: getEnv("LOG_LEVEL", "info"),
			File:  getEnv("LOG_FILE", "./logs/app.log"),
		},
		Frontend: FrontendConfig{
			StaticPath: getEnv("FRONTEND_STATIC_PATH", "./frontend/dist"),
		},
	}

	globalConfig = config
	return config, nil
}

// Get 获取全局配置
func Get() *Config {
	if globalConfig == nil {
		log.Fatal("Config not loaded. Call config.Load() first.")
	}
	return globalConfig
}

func getEnv(key, defaultValue string) string {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	return value
}

func getEnvInt(key string, defaultValue int) int {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	intValue, err := strconv.Atoi(value)
	if err != nil {
		return defaultValue
	}
	return intValue
}

