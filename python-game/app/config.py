from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """应用配置"""
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = True
    spring_boot_url: str = "http://localhost:8080/api"

    class Config:
        env_file = ".env"


@lru_cache
def get_settings() -> Settings:
    return Settings()
